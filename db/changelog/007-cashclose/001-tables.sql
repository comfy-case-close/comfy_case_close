-- ============================================================================
-- 007 CASHCLOSE (FEATURE) - the shift close, the centre of the system
--
-- ── Major changes from the original design ─────────────────────────────────
--   1. cash_movement + cash_diff_explanation + tip  -> MERGED into one ledger
--   2. cash_movement_type + expense_category + cash_diff_reason
--        -> MERGED into platform.movement_kind (SCD-2)
--   3. Every total column removed from cash_close; computed in versioned views
--   4. SIGN CONVENTION INVERTED (read below before touching the UI)
--
-- ── cash_close column categories ───────────────────────────────────────────
--   * typed in : withdrawal_amount, note
--   * from POS : pos_expected_cash (locked when expected_cash_source = POS_SYNC)
--   * snapshot : applied_diff_allowed_abs, applied_diff_alert_abs, is_late,
--                calc_version - written once at submit, immutable after
--   * GONE     : counted_cash, explained_total, expense_total, eod_expense_total,
--   * GONE     : counted_cash, explained_total, expense_total, eod_expense_total,
--                tips_total, tips_in_drawer_total, cash_difference,
--                unexplained_difference, cash_remaining, risk_level,
--                approved_by, approved_at, manager_review_note
--                -> derived, or moved to cash_close_decision
--
-- ── Two decision ledgers, one pattern ──────────────────────────────────────
--   cash_close_decision      : decisions on a whole close  (settles the residual)
--   cash_movement_decision   : decisions on one ledger line (settles what is explained)
-- Both are insert-only, both keep old_status/new_status, both sit beside a
-- current-state column on their parent.
--
-- ── SIGN CONVENTION ────────────────────────────────────────────────────────
--   signed_amount  < 0  => cash OUT of the drawer (an expense)
--   signed_amount  > 0  => cash IN to the drawer (tip left inside, change fund)
--   cash_difference = counted_cash - pos_expected_cash
--                  < 0  => SHORT
--                  > 0  => OVER
--
--   Both live in the same sign space, so
--       unexplained = cash_difference - explained - pending
--   contains no inversion. The old convention (pos - counted, shortage positive)
--   forced every reader to remember "short is positive", a recurring source of
--   arithmetic bugs.
--
-- ── DRAWER IDENTITY ────────────────────────────────────────────────────────
--   cash_remaining = counted_cash - withdrawal_amount
--                    - SUM(abs(signed_amount)) over lines that leave the drawer
--   No longer a written column checked against its parts - it IS the definition,
--   so it cannot be violated and the old two-point check is gone.
--
-- DELIBERATE DENORMALISATION: every child table carries business_id. That breaks
-- 3NF (movement -> cash_close -> business), but it is safe because the composite
-- FK (cash_close_id, business_id) makes a mismatch mechanically impossible and
-- cash_close.business_id is immutable. In exchange, RLS applies directly to child
-- tables without joining up to the parent on every query. ADR-0003 section 8.
-- ============================================================================

CREATE TABLE cashclose.cash_close (
  cash_close_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cash_close_code      TEXT NOT NULL,
  business_id          UUID NOT NULL REFERENCES identity.business(business_id),
  created_by           UUID,
  branch_id            UUID NOT NULL,
  shift_type_id        UUID NOT NULL,
  business_date        DATE NOT NULL,

  status               shared.close_status NOT NULL DEFAULT 'DRAFT',

  -- WHO approved and WHEN are not stored here: they are the newest APPROVE row
  -- in cashclose.cash_close_decision. `status` stays denormalised because it is filtered on
  -- constantly (idx_close_pending); the approver is only ever displayed, so it
  -- is derived. Cache what you filter by, derive what you show.
  submitted_by         UUID,
  submitted_at         TIMESTAMPTZ,
  voided_at            TIMESTAMPTZ,

  -- Expected cash revenue. Under POS_SYNC it comes from integration and staff
  -- cannot edit it. Under MANUAL it must be flagged, because then the person
  -- counting the drawer also supplied the yardstick.
  pos_expected_cash    shared.d_money_nonneg NOT NULL DEFAULT 0,
  expected_cash_source shared.expected_cash_source NOT NULL DEFAULT 'MANUAL',
  pos_shift_sales_id   UUID,

  withdrawal_amount    shared.d_money_nonneg NOT NULL DEFAULT 0,

  -- ── snapshot at submit ─────────────────────────────────────────────────
  -- Config thresholds are copied here so this document can still explain itself
  -- after the owner raises or lowers them.
  applied_diff_allowed_abs shared.d_money_nonneg,
  applied_diff_alert_abs   shared.d_money_nonneg,

  -- is_late is not derivable: it compares submitted_at against the shift deadline
  -- and the business timezone AS THEY WERE AT SUBMIT TIME. Moving the deadline
  -- next year must not turn a punctual close into a late one.
  is_late              BOOLEAN NOT NULL DEFAULT false,

  -- The formula version that produced the figures a manager signed off on.
  -- Changing a formula means adding v_close_calc_vN, never editing the old view.
  -- An APPROVED close keeps its version forever.
  calc_version         TEXT NOT NULL DEFAULT 'v1',

  -- Staff's own note. There is no manager_review_note: a reviewer's comment
  -- belongs to the DECISION that carried it, so it lives in the close decision
  -- ledger's note column - the
  -- same place decision_note lives one level down. Keeping a single "latest
  -- review note" column would lose every earlier comment on a close that was
  -- rejected and resubmitted.
  note                 TEXT,

  -- created_at is when the DRAFT was opened; submitted_at is when it was handed
  -- in. They are the same instant in the legacy spreadsheet only because that
  -- sheet had no draft stage. This system does: openDraft at the start of the
  -- shift, submit at the end. The gap between them is the shift itself.
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

  UNIQUE (business_id, cash_close_code),
  UNIQUE (cash_close_id, business_id),

  -- branch and shift must belong to the SAME business as the close
  FOREIGN KEY (branch_id,          business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (shift_type_id,      business_id) REFERENCES identity.shift_type(shift_type_id, business_id),
  FOREIGN KEY (created_by,         business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (submitted_by,       business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (pos_shift_sales_id, business_id) REFERENCES integration.shift_sales(shift_sales_id, business_id),

  CONSTRAINT ck_close_submitted_fields
    CHECK (status IN ('DRAFT','VOIDED') OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL)),
  CONSTRAINT ck_close_voided_fields
    CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL)),
  CONSTRAINT ck_close_thresholds_snapshotted
    CHECK (status IN ('DRAFT','VOIDED')
           OR (applied_diff_allowed_abs IS NOT NULL AND applied_diff_alert_abs IS NOT NULL)),
  -- claiming POS as the source requires pointing at the actual record
  CONSTRAINT ck_close_pos_source
    CHECK (expected_cash_source <> 'POS_SYNC' OR pos_shift_sales_id IS NOT NULL)
);

-- One LIVE close per branch, shift and date; a voided one can be redone.
-- Comfy's real data had two branch+date+shift collisions, and BOTH were abandoned
-- half-entries (one with pos_expected_cash = 0, one entirely null) rather than two
-- genuine shifts. This index blocks exactly that kind of debris.
CREATE UNIQUE INDEX uq_close_live ON cashclose.cash_close
  (branch_id, shift_type_id, business_date) WHERE status <> 'VOIDED';

CREATE INDEX idx_close_branch_date ON cashclose.cash_close (business_id, branch_id, business_date DESC);
CREATE INDEX idx_close_pending     ON cashclose.cash_close (business_id, status)
  WHERE status IN ('SUBMITTED','PENDING_REVIEW');
CREATE INDEX idx_close_creator     ON cashclose.cash_close (business_id, created_by);
CREATE INDEX idx_close_submitter   ON cashclose.cash_close (business_id, submitted_by);
CREATE INDEX idx_close_manual_cash ON cashclose.cash_close (business_id, business_date)
  WHERE expected_cash_source = 'MANUAL';

-- ----------------------------------------------------------------------------
-- Child tables
-- ----------------------------------------------------------------------------
CREATE TABLE cashclose.close_attachment (
  attachment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cash_close_id UUID NOT NULL,
  business_id   UUID NOT NULL,
  file_id       UUID NOT NULL,
  file_kind     shared.file_kind NOT NULL,
  attached_by   UUID,
  attached_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (attachment_id, cash_close_id),
  FOREIGN KEY (cash_close_id, business_id) REFERENCES cashclose.cash_close(cash_close_id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (file_id,       business_id) REFERENCES files.stored_file(file_id, business_id),
  FOREIGN KEY (attached_by,   business_id) REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_close_attachment ON cashclose.close_attachment (business_id, cash_close_id);

-- ----------------------------------------------------------------------------
-- cash_denomination_line - THE ONLY SOURCE of counted cash
--
-- cash_close no longer has a counted_cash column; the total is the sum here.
-- In Comfy's real data 16 of 181 closes had a stored total that disagreed with
-- this count, because duplicate IDs appended a recount instead of overwriting it.
-- The UNIQUE below makes that impossible, and removing the stored total removes
-- the place where it could happen at all.
-- ----------------------------------------------------------------------------
CREATE TABLE cashclose.cash_denomination_line (
  line_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cash_close_id   UUID NOT NULL,
  business_id     UUID NOT NULL,
  denomination_id SMALLINT NOT NULL REFERENCES platform.denomination(denomination_id),
  quantity        INT NOT NULL CHECK (quantity > 0),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (cash_close_id, denomination_id),
  FOREIGN KEY (cash_close_id, business_id) REFERENCES cashclose.cash_close(cash_close_id, business_id) ON DELETE CASCADE
);
CREATE INDEX idx_denom_line_close ON cashclose.cash_denomination_line (business_id, cash_close_id);

-- ============================================================================
-- cash_movement - THE cash ledger for a shift
--
-- Merged from three former tables: cash_movement + cash_diff_explanation + tip.
-- Anything that makes the drawer disagree with the POS, or takes cash out of it,
-- is a row here: supply purchases, end-of-day parking money, tips, small-change
-- swaps, borrowing to give change, a shift lead topping up a shortfall, an unpaid
-- bill, a POS keying error, a miscount.
--
-- effect_type sits BOTH here and on movement_kind, locked together by the
-- composite FK (kind_sk, effect_type). That is deliberate: a PostgreSQL CHECK
-- cannot join to another table, so for the database itself to reject "an expense
-- with a positive amount" the effect must be on this row. The FK makes the copy
-- incapable of disagreeing with the kind - it is not a copy that can drift.
-- ============================================================================
CREATE TABLE cashclose.cash_movement (
  movement_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cash_close_id         UUID NOT NULL,
  business_id           UUID NOT NULL,

  kind_sk               BIGINT NOT NULL,
  effect_type           TEXT   NOT NULL,       -- locked by the composite FK above

  -- negative = cash out, positive = cash in. See SIGN CONVENTION at the top.
  -- No abs_amount column: abs() in a query costs nothing, and a stored copy is
  -- one more thing that can disagree with its source. The UI shows abs().
  signed_amount         shared.d_money NOT NULL CHECK (signed_amount <> 0),

  -- There is NO person_or_vendor column. It existed in the old sheet and in
  -- practice became a second description field: the real data holds first names
  -- ("Su", "Khang"), goods ("Sua + cam + duong"), objects ("Bon cau"), services
  -- ("Bao hanh ghe don") and whole sentences ("Anh teo bam lon bill"), plus many
  -- blanks. Nothing groupable, and undefined for a tip. Whatever the staff want
  -- to say about a counterparty goes in `description`.
  --
  -- Grouping already exists at the right level: movement_kind.expense_category.
  -- Real per-supplier analysis would need a vendor dimension with an FK and a
  -- controlled list, not a free-text column - a later feature, not this one.
  staff_user_id         UUID,   -- the employee involved: received a tip, topped up the drawer
  description           TEXT,
  receipt_attachment_id UUID,

  -- Per-line approval. A declared but unreviewed line does NOT count towards
  -- `explained` - it lands in `pending`, so the manager sees three figures:
  -- explained, awaiting review, and genuinely unexplained.
  approval_status       TEXT NOT NULL DEFAULT 'PENDING'
                          CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
  -- "decided", not "approved": rejecting is also a decision, and knowing WHO
  -- rejected matters more than knowing who agreed.
  decided_by            UUID,
  decided_at            TIMESTAMPTZ,
  -- Reason for the CURRENT decision. A trigger copies it into movement_decision,
  -- which keeps every reason ever given.
  decision_note         TEXT,

  created_by            UUID,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

  FOREIGN KEY (kind_sk, effect_type)
    REFERENCES platform.movement_kind(kind_sk, effect_type),
  FOREIGN KEY (cash_close_id, business_id) REFERENCES cashclose.cash_close(cash_close_id, business_id) ON DELETE CASCADE,
  -- a receipt must be an attachment of THIS close
  FOREIGN KEY (receipt_attachment_id, cash_close_id)
    REFERENCES cashclose.close_attachment(attachment_id, cash_close_id),
  FOREIGN KEY (created_by,    business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (decided_by,    business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (staff_user_id, business_id) REFERENCES identity.staff(staff_id, business_id),

  -- The sign must match the direction of cash. NO_CASH_FLOW carries both signs:
  -- "customer forgot to pay 100k" leaves the drawer short (negative), "POS
  -- double-counted" leaves it over (positive), and no note actually moved.
  CONSTRAINT ck_movement_sign CHECK (
    (effect_type = 'CASH_OUT'     AND signed_amount < 0) OR
    (effect_type = 'CASH_IN'      AND signed_amount > 0) OR
    (effect_type = 'NO_CASH_FLOW')
  ),
  -- "note required" / "receipt required" are NOT here: they depend on the kind
  -- (movement_kind.requires_note / requires_receipt) and a CHECK cannot join.
  -- Enforced in fn_close_child_guard, 002 section 13.1.
  --
  -- Decided (approved OR rejected) means we must know who and when. Pending means
  -- both must be empty, so nobody can "quietly approve" and then walk it back.
  CONSTRAINT ck_movement_decided_fields CHECK (
    (approval_status <> 'PENDING') = (decided_by IS NOT NULL AND decided_at IS NOT NULL)
  ),

  -- Target for the composite FK from cash_movement_decision, so that table can
  -- carry business_id (required for RLS) without also carrying cash_close_id.
  UNIQUE (movement_id, business_id)
);
CREATE INDEX idx_movement_close   ON cashclose.cash_movement (business_id, cash_close_id);
CREATE INDEX idx_movement_kind    ON cashclose.cash_movement (business_id, kind_sk);
CREATE INDEX idx_movement_pending ON cashclose.cash_movement (business_id, cash_close_id)
  WHERE approval_status = 'PENDING';

-- ============================================================================
-- cash_movement_decision - decision ledger for ONE ledger line. INSERT ONLY.
--
-- cash_movement keeps the CURRENT status (fast to read, impossible to
-- double-count); this table keeps the SEQUENCE of decisions. Exactly the pairing
-- cash_close.status + cashclose.cash_close_decision uses one level up.
--
-- ── This is an EVENT LOG, not an SCD-2 dimension ───────────────────────────
--   SCD-2 dimension          | event log (this table)
--   -------------------------|-------------------------------------------
--   one row per VERSION      | one row per THING THAT HAPPENED
--   "what did it look like   | "what happened, when, and who did it"
--    on date X"              |
--   needs valid_from/to      | an event is instantaneous - a validity range
--                            | would model a duration the row does not have
--   e.g. platform.movement_kind | this table, cash_close_decision, audit_log
--
-- Hence decided_at, not valid_from/valid_to, and no is_current.
--
-- ── Why old_status AND new_status ──────────────────────────────────────────
-- new_status alone is ambiguous: PENDING can be arrived at from APPROVED
-- ("the manager un-approved it") or from REJECTED ("staff will fix and
-- resubmit"). Those are different stories. Keeping both also means one row reads
-- on its own, without reconstructing a chain that a missing row would corrupt.
--
-- ── Why the amount is snapshotted here ─────────────────────────────────────
-- Status history alone is not enough. Approve a 523,000 line, reopen it, change
-- it to 5,230,000, approve again: the status log would read
-- PENDING->APPROVED->PENDING->APPROVED and show nothing about the amount. Each
-- decision therefore records the figures AS THEY WERE at that moment, so the
-- ledger reads "approved at 523,000 ... approved again at 5,230,000".
--
-- Note there is no cash_close_id: it is reachable through movement_id. Only
-- business_id is denormalised, because the RLS loop in 900-rls keys off that
-- column, and the composite FK below keeps it honest.
-- ============================================================================
CREATE TABLE cashclose.cash_movement_decision (
  decision_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  movement_id   UUID NOT NULL,
  business_id   UUID NOT NULL,

  old_status    TEXT NOT NULL,
  new_status    TEXT NOT NULL,

  -- the line as it stood when this decision was taken
  kind_sk       BIGINT NOT NULL REFERENCES platform.movement_kind(kind_sk),
  signed_amount shared.d_money NOT NULL,

  decided_by    UUID,
  decided_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  note          TEXT,

  FOREIGN KEY (movement_id, business_id)
    REFERENCES cashclose.cash_movement(movement_id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (decided_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  CONSTRAINT ck_movement_decision_changed CHECK (old_status <> new_status)
);
CREATE INDEX idx_movement_decision ON cashclose.cash_movement_decision (business_id, movement_id, decided_at);
CREATE TRIGGER trg_movement_decision_append_only
  BEFORE UPDATE OR DELETE ON cashclose.cash_movement_decision
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

-- ----------------------------------------------------------------------------
-- cash_close_decision - decision ledger for a WHOLE close. INSERT ONLY.
--
-- Same shape and same purpose as cash_movement_decision, one level up: the table
-- holds the SEQUENCE of decisions while cash_close.status holds the current one.
--
-- ── What close-level approval decides that line-level does not ─────────────
-- Per-line decisions settle what is EXPLAINED. This one settles the RESIDUAL -
-- the part nobody declared. A shift 200,000 short with zero declared lines has no
-- line to approve; the close itself is the only decision point. It is also what
-- freezes the child rows, and what analytics filters on.
--
-- Both old_status and new_status are kept, for two reasons:
--   1. Not every action changes state - REQUEST_CHANGES can leave a close in
--      PENDING_REVIEW. With only the new status you could not tell "changes were
--      requested" from "nobody has touched it".
--   2. One row reads on its own: "DRAFT -> SUBMITTED" needs no context. With only
--      the new status you would have to read the previous row, and one missing
--      row would corrupt the whole chain.
--
-- A close can go REJECTED, back to DRAFT, then APPROVED. This table keeps that
-- chain, including every reviewer comment - which is why cash_close has no
-- manager_review_note column: a single "latest note" would drop all the others.
-- ----------------------------------------------------------------------------
CREATE TABLE cashclose.cash_close_decision (
  decision_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cash_close_id UUID NOT NULL,
  business_id   UUID NOT NULL,
  action        shared.approval_action NOT NULL,
  acted_by      UUID NOT NULL,
  acted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  old_status    shared.close_status NOT NULL,
  new_status    shared.close_status NOT NULL,
  note          TEXT,
  FOREIGN KEY (cash_close_id, business_id) REFERENCES cashclose.cash_close(cash_close_id, business_id),
  FOREIGN KEY (acted_by,      business_id) REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_close_decision ON cashclose.cash_close_decision (business_id, cash_close_id, acted_at);
CREATE TRIGGER trg_close_decision_append_only
  BEFORE UPDATE OR DELETE ON cashclose.cash_close_decision
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

-- ----------------------------------------------------------------------------
-- fund_withdrawal - the owner taking accumulated cash out of a branch drawer.
--
-- Different grain from everything above: it groups by branch and PERIOD
-- (period_from..period_to), not by cash_close_id.
--   system_withdraw_amount  ~ SUM(cash_close.withdrawal_amount) for that period
--   actual_received_amount    typed in from the bank statement
--   variance_amount           the gap worth investigating
--
-- A non-zero variance means cash went missing IN TRANSIT between the branch
-- drawer and the owner - a loss no individual close can reveal, because each
-- shift balances on its own.
-- ----------------------------------------------------------------------------
CREATE TABLE cashclose.fund_withdrawal (
  fund_withdrawal_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  fund_withdrawal_code   TEXT NOT NULL,
  business_id            UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id              UUID NOT NULL,
  period_type            shared.fund_period NOT NULL,
  period_from            DATE NOT NULL,
  period_to              DATE NOT NULL,
  created_by             UUID NOT NULL,
  system_pot_before      shared.d_money NOT NULL DEFAULT 0,
  system_withdraw_amount shared.d_money_nonneg NOT NULL,
  actual_received_amount shared.d_money_nonneg NOT NULL,
  variance_amount        shared.d_money GENERATED ALWAYS AS
                           (actual_received_amount - system_withdraw_amount) STORED,
  system_pot_after       shared.d_money NOT NULL DEFAULT 0,
  status                 shared.fund_status NOT NULL DEFAULT 'OPEN',
  note                   TEXT,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (business_id, fund_withdrawal_code),
  CHECK (period_to >= period_from),
  FOREIGN KEY (branch_id,  business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (created_by, business_id) REFERENCES identity.staff(staff_id, business_id),
  -- no two live withdrawals at one branch may cover overlapping periods
  EXCLUDE USING gist (
    branch_id WITH =,
    daterange(period_from, period_to, '[]') WITH &&
  ) WHERE (status <> 'VOIDED')
);
CREATE INDEX idx_fund_branch ON cashclose.fund_withdrawal (business_id, branch_id, period_from DESC);
CREATE TRIGGER trg_fund_touch BEFORE UPDATE ON cashclose.fund_withdrawal
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

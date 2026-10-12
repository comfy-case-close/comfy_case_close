-- ============================================================================
-- 005 NOTIFY (CORE) - sends email/Zalo/push, deduplicates, tracks acknowledgement
--
-- notify does NOT decide WHEN an alert is warranted; that is the emitting
-- module's job. notify answers only: to whom, over which channel, was it sent,
-- and did anyone acknowledge it.
--
-- GRAIN: one row = one notification, to one recipient, over one channel.
--        A close alerting two managers by email and Zalo produces FOUR rows.
--
-- source_module / source_entity_id form a SOFT link (no FK) back to the emitting
-- module. That is what keeps notify in the CORE group: it depends on no FEATURE
-- module, so the "CORE does not depend on FEATURE" rule of ADR-0001 holds. The
-- price is no referential integrity on that pair - a deliberate trade.
-- ============================================================================

CREATE TABLE notify.alert (
  alert_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id       UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id         UUID,

  -- soft link to the emitting module: 'cashclose' / 'workforce' / 'inventory'
  source_module     TEXT NOT NULL,
  source_entity_id  UUID,

  recipient_user_id UUID,
  recipient_address TEXT,        -- the address actually used, snapshotted at send
  subject_user_id   UUID,        -- who the alert is ABOUT (input for the AI agent)

  alert_type        TEXT NOT NULL,
  severity          shared.alert_severity NOT NULL,
  message           TEXT NOT NULL,

  channel           shared.alert_channel NOT NULL,
  status            shared.alert_status NOT NULL DEFAULT 'PENDING',
  sent_at           TIMESTAMPTZ,
  failure_reason    TEXT,
  retry_count       INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),

  -- The two most valuable columns here. Without them you cannot measure how many
  -- HIGH alerts were ignored - and an ignored alert is worse than no alert,
  -- because it manufactures a false sense of safety.
  acknowledged_by   UUID,
  acknowledged_at   TIMESTAMPTZ,

  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_alert_has_target
    CHECK (recipient_user_id IS NOT NULL OR recipient_address IS NOT NULL),
  CONSTRAINT ck_alert_sent_ts
    CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
  CONSTRAINT ck_alert_failure_reason
    CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
  CONSTRAINT ck_alert_ack_pair
    CHECK ((acknowledged_by IS NULL) = (acknowledged_at IS NULL)),

  FOREIGN KEY (branch_id,         business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (recipient_user_id, business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (subject_user_id,   business_id) REFERENCES identity.staff(staff_id, business_id),
  FOREIGN KEY (acknowledged_by,   business_id) REFERENCES identity.staff(staff_id, business_id)
);

CREATE INDEX idx_alert_source    ON notify.alert (business_id, source_module, source_entity_id);
CREATE INDEX idx_alert_branch    ON notify.alert (business_id, branch_id, created_at DESC);
CREATE INDEX idx_alert_recipient ON notify.alert (business_id, recipient_user_id, created_at DESC);
CREATE INDEX idx_alert_subject   ON notify.alert (business_id, subject_user_id) WHERE subject_user_id IS NOT NULL;
CREATE INDEX idx_alert_outbox    ON notify.alert (created_at) WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_alert_unacked   ON notify.alert (business_id, severity, created_at) WHERE acknowledged_at IS NULL;

-- Deduplication: a scheduler re-running every 5 minutes cannot alert the same
-- person about the same issue on the same document over the same channel twice.
CREATE UNIQUE INDEX uq_alert_dedup ON notify.alert
  (source_module, source_entity_id, alert_type, recipient_user_id, channel)
  WHERE source_entity_id IS NOT NULL AND status <> 'CANCELLED';

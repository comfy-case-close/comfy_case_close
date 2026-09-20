-- ============================================================================
-- 006 INTEGRATION (CORE) - third-party POS integration
--
-- THE PROBLEM IT SOLVES
--   In the old spreadsheet, staff typed the shift's cash revenue by hand. That
--   figure is the baseline the gap is measured against:
--       gap = counted cash - cash revenue (hand-typed)
--   The person counting the money also typed the yardstick. Hiding a shortfall
--   took one keystroke: the entire control collapsed at a SINGLE INPUT FIELD.
--
--   integration takes that figure STRAIGHT FROM THE POS. Staff cannot touch it.
--
-- A MICROKERNEL PLUG-IN: Comfy uses iPOS; a later customer may use KiotViet,
-- Sapo or Haravan. Adding a vendor means ONE new PosAdapter class - cashclose
-- does not change.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- pos_connection - one branch to one POS connection
--
-- SECURITY: credential_ref is an ID inside a secret manager (AWS Secrets Manager
-- or Vault), NEVER the secret itself. This is CUSTOMER data - one database leak
-- would expose every customer's POS credentials, and they could lose real money.
-- ----------------------------------------------------------------------------
CREATE TABLE integration.pos_connection (
  connection_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id     UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id       UUID NOT NULL,
  vendor          shared.pos_vendor NOT NULL,
  external_store_id TEXT,                    -- the store id on the vendor's side
  credential_ref  TEXT NOT NULL,             -- <- NEVER the secret itself
  base_url        TEXT,
  is_active       BOOLEAN NOT NULL DEFAULT true,
  last_sync_at    TIMESTAMPTZ,
  last_sync_status shared.sync_status,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- one live connection per branch per vendor
  UNIQUE (branch_id, vendor),
  UNIQUE (connection_id, business_id),
  FOREIGN KEY (branch_id, business_id) REFERENCES identity.branch(branch_id, business_id),

  -- credential_ref must obviously be a reference, so a raw token cannot be pasted
  CONSTRAINT ck_credential_is_reference
    CHECK (credential_ref ~ '^(secretsmanager|vault|env):')
);
CREATE INDEX idx_pos_conn_business ON integration.pos_connection (business_id, branch_id);
CREATE TRIGGER trg_pos_conn_touch BEFORE UPDATE ON integration.pos_connection
  FOR EACH ROW EXECUTE FUNCTION shared.fn_touch_updated_at();

-- ----------------------------------------------------------------------------
-- shift_sales - NORMALISED sales data
--
-- iPOS returns JSON shape A, KiotViet shape B, Sapo shape C; all of them are
-- converted into this one. No other service knows which vendor is in use.
--
-- APPEND-ONLY: when the POS restates a figure, insert a NEW row (revision + 1)
-- rather than updating. An approved close must remain explainable by the numbers
-- it SAW at approval time, not by today's numbers.
-- ----------------------------------------------------------------------------
CREATE TABLE integration.shift_sales (
  shift_sales_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id     UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id       UUID NOT NULL,
  shift_type_id   UUID NOT NULL,
  business_date   DATE NOT NULL,

  cash_sales      shared.d_money_nonneg NOT NULL DEFAULT 0,
  card_sales      shared.d_money_nonneg NOT NULL DEFAULT 0,
  ewallet_sales   shared.d_money_nonneg NOT NULL DEFAULT 0,
  other_sales     shared.d_money_nonneg NOT NULL DEFAULT 0,
  gross_sales     shared.d_money_nonneg GENERATED ALWAYS AS
                    (cash_sales + card_sales + ewallet_sales + other_sales) STORED,
  order_count     INT NOT NULL DEFAULT 0 CHECK (order_count >= 0),

  source_vendor   shared.pos_vendor NOT NULL,
  connection_id   UUID,
  revision        INT NOT NULL DEFAULT 1 CHECK (revision >= 1),
  synced_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

  UNIQUE (branch_id, shift_type_id, business_date, revision),
  UNIQUE (shift_sales_id, business_id),
  FOREIGN KEY (branch_id,     business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (shift_type_id, business_id) REFERENCES identity.shift_type(shift_type_id, business_id),
  FOREIGN KEY (connection_id, business_id) REFERENCES integration.pos_connection(connection_id, business_id)
);
CREATE INDEX idx_shift_sales_lookup ON integration.shift_sales
  (business_id, branch_id, business_date, shift_type_id, revision DESC);

CREATE TRIGGER trg_shift_sales_append_only BEFORE UPDATE OR DELETE ON integration.shift_sales
  FOR EACH ROW EXECUTE FUNCTION shared.fn_forbid_mutation();

-- Latest revision per (branch, date, shift) - this is what cashclose reads
CREATE VIEW integration.v_shift_sales_latest AS
SELECT DISTINCT ON (branch_id, shift_type_id, business_date)
       shift_sales_id, business_id, branch_id, shift_type_id, business_date,
       cash_sales, card_sales, ewallet_sales, other_sales, gross_sales,
       order_count, source_vendor, revision, synced_at
FROM integration.shift_sales
ORDER BY branch_id, shift_type_id, business_date, revision DESC;

-- ----------------------------------------------------------------------------
-- sync_log - for reconciling when figures disagree, and for retrying when the
-- POS goes down (which at 22:00 is routine, not exceptional).
--
-- Raw payloads go to files (raw_payload_file_id), not into a JSONB column:
-- 200 tenants x 3 branches x 3 shifts is 1,800 payloads a day.
-- ----------------------------------------------------------------------------
CREATE TABLE integration.sync_log (
  sync_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id        UUID NOT NULL REFERENCES identity.business(business_id),
  connection_id      UUID NOT NULL,
  sync_type          TEXT NOT NULL,           -- SHIFT_SALES | MENU | INVENTORY
  period_from        TIMESTAMPTZ NOT NULL,
  period_to          TIMESTAMPTZ NOT NULL,
  status             shared.sync_status NOT NULL,
  rows_ingested      INT NOT NULL DEFAULT 0,
  raw_payload_file_id UUID,
  error_message      TEXT,
  retry_count        INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at        TIMESTAMPTZ,

  CHECK (period_to >= period_from),
  CONSTRAINT ck_sync_failed_has_reason
    CHECK (status <> 'FAILED' OR error_message IS NOT NULL),
  FOREIGN KEY (connection_id,       business_id) REFERENCES integration.pos_connection(connection_id, business_id),
  FOREIGN KEY (raw_payload_file_id, business_id) REFERENCES files.stored_file(file_id, business_id)
);
CREATE INDEX idx_sync_log_conn ON integration.sync_log (business_id, connection_id, started_at DESC);
CREATE INDEX idx_sync_log_fail ON integration.sync_log (started_at) WHERE status = 'FAILED';

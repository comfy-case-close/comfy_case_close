-- ============================================================================
-- 004 FILES (CORE) - stores bytes, issues signed URLs, keeps immutable metadata
--
-- files OWNS the bytes. What a file MEANS belongs to the module that uses it -
-- cashclose knows "this is the POS report for CC-20260815-TX-EVENING", files only
-- knows "2MB image, sha256 abc".
--
-- Other modules keep file_id plus an IMMUTABLE SNAPSHOT (url, sha256), which is
-- safe because a file never changes after upload. The day file-service is
-- extracted, dropping the constraint is the entire migration: no consumer, no
-- re-sync job, no monitoring. Contrast with branch names, which do change, so
-- other modules must join instead of copying. ADR-0001 section 8.5.
-- ============================================================================

CREATE TABLE files.stored_file (
  file_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id      UUID NOT NULL REFERENCES identity.business(business_id),
  branch_id        UUID,
  file_kind        shared.file_kind NOT NULL,
  file_name        TEXT NOT NULL,
  content_type     TEXT NOT NULL,
  byte_size        BIGINT NOT NULL CHECK (byte_size > 0),
  sha256           TEXT NOT NULL,
  storage_provider TEXT NOT NULL DEFAULT 'S3',
  storage_key      TEXT NOT NULL,
  public_url       TEXT,
  uploaded_by      UUID,
  uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- OCR: files triggers it, ai-service processes, the result lands back here
  ocr_status       TEXT NOT NULL DEFAULT 'NONE'
                   CHECK (ocr_status IN ('NONE','PENDING','DONE','FAILED')),
  ocr_text         TEXT,
  ocr_extracted    JSONB,

  UNIQUE (file_id, business_id),
  FOREIGN KEY (branch_id,   business_id) REFERENCES identity.branch(branch_id, business_id),
  FOREIGN KEY (uploaded_by, business_id) REFERENCES identity.staff(staff_id, business_id)
);
CREATE INDEX idx_file_business ON files.stored_file (business_id, uploaded_at DESC);
CREATE INDEX idx_file_sha      ON files.stored_file (business_id, sha256);
CREATE INDEX idx_file_ocr_queue ON files.stored_file (uploaded_at) WHERE ocr_status = 'PENDING';

-- identity.staff.avatar_file_id points here. This FK is what gets DROPped the day
-- file-service is extracted; the snapshot (avatar_url, avatar_sha256) is what
-- makes that harmless.
ALTER TABLE identity.staff
  ADD CONSTRAINT fk_staff_avatar
  FOREIGN KEY (avatar_file_id) REFERENCES files.stored_file(file_id);

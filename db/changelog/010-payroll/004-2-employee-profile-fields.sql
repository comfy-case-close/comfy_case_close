-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.4-employee-profile-and-contract-fields.xml
-- Employee sheet fields that identity.staff must not carry. bank_name becomes a bank_code reference.

ALTER TABLE payroll.employee_profile
  ADD COLUMN date_of_birth             DATE,
  ADD COLUMN gender                    payroll.gender,
  ADD COLUMN marital_status            payroll.marital_status,
  ADD COLUMN children_count            SMALLINT CHECK (children_count >= 0),
  ADD COLUMN ethnicity                 TEXT,
  ADD COLUMN religion                  TEXT,
  ADD COLUMN nationality               TEXT NOT NULL DEFAULT 'VN',
  ADD COLUMN national_id_no            TEXT,
  ADD COLUMN national_id_issued_on     DATE,
  ADD COLUMN national_id_issued_place  TEXT,
  ADD COLUMN social_insurance_no       TEXT,
  ADD COLUMN tax_code                  TEXT,
  ADD COLUMN education_level           payroll.education_level,
  ADD COLUMN education_school          TEXT,
  ADD COLUMN education_major           TEXT,
  ADD COLUMN graduation_year           SMALLINT CHECK (graduation_year BETWEEN 1950 AND 2100),
  ADD COLUMN education_grade           payroll.education_grade,
  ADD COLUMN personal_email            TEXT,
  ADD COLUMN address_street            TEXT,
  ADD COLUMN address_ward              TEXT,
  ADD COLUMN address_province_code     TEXT REFERENCES payroll.province(province_code),
  ADD COLUMN recruitment_source        payroll.recruitment_source,
  ADD COLUMN termination_reason        payroll.termination_reason,
  ADD COLUMN bank_code                 TEXT REFERENCES payroll.bank(bank_code),
  ADD COLUMN bank_account_name         TEXT,
  ADD CONSTRAINT ck_employee_profile_termination
    CHECK (termination_reason IS NULL OR terminated_on IS NOT NULL);

UPDATE payroll.employee_profile p
   SET bank_code = b.bank_code
  FROM payroll.bank b
 WHERE lower(btrim(p.bank_name)) IN (lower(b.short_name), lower(b.bank_code), lower(b.bank_name));

ALTER TABLE payroll.employee_profile DROP COLUMN bank_name;

CREATE INDEX idx_employee_profile_birth_month
  ON payroll.employee_profile (business_id, (EXTRACT(MONTH FROM date_of_birth)))
  WHERE date_of_birth IS NOT NULL;

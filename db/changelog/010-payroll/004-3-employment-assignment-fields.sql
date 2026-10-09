-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.4-employee-profile-and-contract-fields.xml
-- Contract number and kind, job level, probation outcome and the supplement allowance of the salary split.

CREATE SEQUENCE payroll.contract_no_seq;
GRANT USAGE ON SEQUENCE payroll.contract_no_seq TO svc_hrm;

ALTER TABLE payroll.employment_assignment
  ADD COLUMN supplement_allowance shared.d_money_nonneg NOT NULL DEFAULT 0,
  ADD COLUMN job_level            payroll.job_level,
  ADD COLUMN contract_kind        payroll.contract_kind,
  ADD COLUMN contract_no          TEXT,
  ADD COLUMN probation_result     payroll.probation_result,
  ADD CONSTRAINT uq_employment_assignment_contract_no UNIQUE (business_id, contract_no),
  ADD CONSTRAINT ck_employment_assignment_probation
    CHECK (probation_result IS NULL OR contract_kind = 'PROBATION');

-- Ported from feat/payroll: db/changelog/payroll/1.0.0.11-contract-template-kind.xml
-- A position has two papers, the labour contract and the probation agreement, so a template carries which one it prints and the version counter runs per position and kind. Existing templates are labour contracts.

CREATE TYPE payroll.contract_template_kind AS ENUM ('LABOR_CONTRACT', 'PROBATION_AGREEMENT');

ALTER TABLE payroll.contract_template
  ADD COLUMN kind payroll.contract_template_kind NOT NULL DEFAULT 'LABOR_CONTRACT';

DROP INDEX payroll.uq_contract_template_version;
CREATE UNIQUE INDEX uq_contract_template_version
  ON payroll.contract_template (business_id, COALESCE(position_id, '00000000-0000-0000-0000-000000000000'::uuid), kind, version);

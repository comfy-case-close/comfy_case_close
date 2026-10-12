-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.5-salary-supplement-and-birthday.xml
-- SALARY_SUPPLEMENT and BIRTHDAY_ALLOWANCE for every existing business; the engine skips a missing component.

INSERT INTO payroll.pay_component
  (business_id, component_code, component_name, component_type, proration_basis, share_scope, applies_to, display_order)
SELECT b.business_id, v.code, v.name, 'EARNING', 'NONE', v.scope::payroll.share_scope, v.applies_to::payroll.applies_to, v.ord
FROM identity.business b
CROSS JOIN (VALUES
  ('SALARY_SUPPLEMENT',  'Phụ cấp bù lương',  'LINE',     'BOTH',     15),
  ('BIRTHDAY_ALLOWANCE', 'Phụ cấp sinh nhật', 'EMPLOYEE', 'BOTH',     90)
) AS v(code, name, scope, applies_to, ord)
ON CONFLICT (business_id, component_code) DO NOTHING;

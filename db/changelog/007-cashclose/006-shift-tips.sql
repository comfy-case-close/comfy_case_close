-- Tips are counted with drawer cash, then paid to the shift's staff.
-- This project has no tip data yet, so the earlier tip kinds can be removed.
DELETE FROM platform.movement_kind
WHERE kind_code IN ('TIP_DIRECT', 'TIP_IN_DRAWER', 'TIP_JAR');

INSERT INTO platform.movement_kind
  (kind_code, effect_type, affects_difference, affects_remaining,
   expense_category, diff_reason_group, requires_receipt, requires_note, display_name)
VALUES ('TIPS', 'CASH_IN', true, true, NULL, 'TIP', false, false,
        'Tips received during the shift')
ON CONFLICT DO NOTHING;

-- The payout ledger was only for a pooled jar balance. The new process pays
-- the shift's declared tips after counting drawer and jar cash together.
DROP TABLE IF EXISTS cashclose.tip_payout;

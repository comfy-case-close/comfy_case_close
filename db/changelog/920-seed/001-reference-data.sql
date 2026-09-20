-- ============================================================================
-- 920 SEED - static lookup data, belonging to no particular business.
-- Idempotent: safe to run repeatedly.
-- ============================================================================

INSERT INTO platform.denomination (currency_code, face_value) VALUES
  ('VND', 500000), ('VND', 200000), ('VND', 100000), ('VND', 50000),
  ('VND', 20000),  ('VND', 10000),  ('VND', 5000),   ('VND', 2000), ('VND', 1000)
ON CONFLICT (currency_code, face_value) DO NOTHING;

-- ============================================================================
-- movement_kind - the starting catalogue for the cash ledger (version 1)
--
-- This vocabulary was read out of Comfy's REAL data (182 closes / 287 movements /
-- 311 explanation rows). The last six kinds (CHANGE_FUND_*, DRAWER_BORROW,
-- STAFF_REIMBURSE, UNPAID_BILL, POS_ERROR, MISCOUNT) are currently crammed into
-- category OTHER or buried in free-text notes, because the old schema only
-- allowed recording an EXPENSE (amount > 0, movement_type = EXPENSE) and had
-- nowhere to put cash coming IN:
--
--   "Swapped 600,000 into small change"      -> CHANGE_FUND_IN
--   "Borrowed 4,000 to give a customer change" -> DRAWER_BORROW
--   "Shift lead covered the shortfall"       -> STAFF_REIMBURSE
--   "Customer forgot to pay"                 -> UNPAID_BILL
--   "Keyed as cash, was actually a transfer" -> POS_ERROR
--   "Miscounted the drawer (3k short)"       -> MISCOUNT
--
-- 55% of old movements sat in category='OTHER'. The point of this list is to
-- drive that number down: every kind added here is one "why is it off" question
-- answered in advance.
--
-- ── How to read the table ──────────────────────────────────────────────────
--   effect  : did cash actually move, and which way
--   diff    : affects_difference - was it already inside the counted cash
--   remain  : affects_remaining  - does it leave the drawer before hand-over
--
-- TIP_IN_DRAWER is the ONLY kind with both flags: the tip is in the drawer when
-- counted (so it explains the surplus) and is then taken out for staff (so it
-- reduces what is left). Verified on a real close: counted 8,180,000, tip 34,000,
-- remaining 8,146,000.
-- ============================================================================
INSERT INTO platform.movement_kind
  (kind_code, effect_type, affects_difference, affects_remaining,
   expense_category, diff_reason_group, requires_receipt, requires_note, display_name) VALUES
  -- in-shift spending: cash out BEFORE the count -> explains the difference
  ('EXPENSE_SUPPLY',      'CASH_OUT',     true,  false, 'SUPPLY',         'NOT_IN_POS',  false, false, 'Supplies'),
  ('EXPENSE_SHIPPING',    'CASH_OUT',     true,  false, 'GOODS_SHIPPING', 'NOT_IN_POS',  false, false, 'Goods shipping'),
  ('EXPENSE_MAINTENANCE', 'CASH_OUT',     true,  false, 'MAINTENANCE',    'NOT_IN_POS',  false, false, 'Repairs and maintenance'),
  ('EXPENSE_UTILITY',     'CASH_OUT',     true,  false, 'UTILITY',        'NOT_IN_POS',  false, false, 'Utilities'),
  ('EXPENSE_OTHER',       'CASH_OUT',     true,  false, 'OTHER',          'NOT_IN_POS',  false, true,  'Other in-shift spending'),

  -- end-of-day spending: cash out AFTER the count -> no effect on the difference
  ('EOD_STAFF_PARKING',   'CASH_OUT',     false, true,  'STAFF_PARKING',  NULL,          false, false, 'Staff parking, end of day'),
  ('EOD_EXPENSE_OTHER',   'CASH_OUT',     false, true,  'OTHER',          NULL,          false, true,  'Other end-of-day spending'),

  -- tips
  ('TIP_IN_DRAWER',       'CASH_IN',      true,  true,  NULL,             'TIP',         false, false, 'Tip left in the drawer'),
  ('TIP_DIRECT',          'NO_CASH_FLOW', false, false, NULL,             'TIP',         false, false, 'Tip handed straight to staff'),

  -- change fund and borrowing: real cash flow, but NOT a cost
  ('CHANGE_FUND_IN',      'CASH_IN',      true,  false, NULL,             'CHANGE_FUND', false, true,  'Small change added to the drawer'),
  ('CHANGE_FUND_OUT',     'CASH_OUT',     true,  false, NULL,             'CHANGE_FUND', false, true,  'Change swapped out of the drawer'),
  ('DRAWER_BORROW',       'CASH_OUT',     true,  false, NULL,             'CHANGE_FUND', false, true,  'Borrowed from the drawer, to be returned'),
  ('STAFF_REIMBURSE',     'CASH_IN',      true,  false, NULL,             'REIMBURSE',   false, true,  'Staff topped up the drawer'),

  -- no cash moved: the number is wrong, not the money
  ('UNPAID_BILL',         'NO_CASH_FLOW', true,  false, NULL,             'UNPAID',      false, true,  'Customer did not pay'),
  ('POS_ERROR',           'NO_CASH_FLOW', true,  false, NULL,             'POS_ERROR',   false, true,  'POS recorded it wrong'),
  ('MISCOUNT',            'NO_CASH_FLOW', true,  false, NULL,             'MISCOUNT',    false, true,  'Drawer was miscounted'),
  ('THEFT_SUSPECTED',     'NO_CASH_FLOW', true,  false, NULL,             'THEFT',       false, true,  'Suspected loss')
ON CONFLICT DO NOTHING;

-- Global default thresholds. Owners override at BUSINESS or BRANCH level.
INSERT INTO platform.app_config (scope, config_key, config_value, description) VALUES
  ('GLOBAL', 'DIFF_ALLOWED_ABS',  '20000',  'Absolute gap treated as normal (VND)'),
  ('GLOBAL', 'DIFF_ALERT_ABS',    '100000', 'Absolute gap that raises an alert (VND)'),
  ('GLOBAL', 'EXPENSE_ALERT_ABS', '500000', 'Shift expense total that raises an alert (VND)'),
  ('GLOBAL', 'SESSION_TTL_HOURS', '12',     'Login session lifetime'),
  ('GLOBAL', 'REQUIRE_POS_IMAGE', 'true',   'Require a POS report photo when submitting')
ON CONFLICT DO NOTHING;

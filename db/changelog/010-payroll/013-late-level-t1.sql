-- Ported from feat/payroll: db/changelog/payroll/1.0.0.13-late-level-t1.xml
-- A third lateness level on the attendance sheet, for rule T1 (the light one, 15 to 29 minutes). LEVEL_1 stays rule T2 and LEVEL_2 rule T3. What each level deducts is read from the lateness rules, not fixed in code.

ALTER TYPE payroll.late_level ADD VALUE IF NOT EXISTS 'LEVEL_0' BEFORE 'LEVEL_1';

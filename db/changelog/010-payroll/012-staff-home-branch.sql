-- Ported from feat/payroll: db/changelog/payroll/1.0.0.12-staff-home-branch.xml
-- The branch an employee is scheduled at by default. Almost every contract defaults to the shared pool (Đa chi nhánh), which says where the cost lands, not where the person works, so shift scheduling keeps its own marker. A store manager may still place someone at another branch with a reason; NULL means anywhere.

ALTER TABLE payroll.staff_scheduling_profile
  ADD COLUMN home_branch_id UUID;

ALTER TABLE payroll.staff_scheduling_profile
  ADD CONSTRAINT staff_scheduling_profile_home_branch_fkey
  FOREIGN KEY (home_branch_id, business_id) REFERENCES identity.branch (branch_id, business_id);

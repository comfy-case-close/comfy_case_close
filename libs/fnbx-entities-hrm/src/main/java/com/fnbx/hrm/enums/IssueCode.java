package com.fnbx.hrm.enums;

import java.util.Set;

/** The checks of spec section 5.10 - what used to be five warning blocks in {@code Canh bao du lieu}. */
public enum IssueCode {
    /** Replaces the Excel {@code #MA?} sentinel. */
    INVALID_ATTENDANCE_CODE,
    /** Same person has {@code paidHours > 0} at more than one branch on the same day ("LECH CN"). */
    BRANCH_CONFLICT_SAME_DAY,
    /** FULLTIME line with no monthly base salary, or PARTTIME with no hourly rate. */
    MISSING_CONTRACT_RATE,
    /** {@code v_leave_balance.remainingDays < 0}. */
    LEAVE_OVERDRAWN,
    /** Reconciliation check (2): net + deductions + employer insurance != labor cost. */
    RECON_TOTAL_MISMATCH,
    /** Reconciliation checks (1)/(5): branch cost view total != labor cost total. */
    RECON_BRANCH_MISMATCH,
    /** Reconciliation check (4): payslip net total != payroll line net total. */
    RECON_PAYSLIP_MISMATCH,
    /** A person with a contract in the period has no date of birth, so the birthday allowance cannot be decided. */
    MISSING_BIRTH_DATE,
    /** A timesheet cell lies outside the period dates after they were edited. */
    TIMESHEET_OUTSIDE_PERIOD,
    /** A scheduled week overlapping the period has no confirmed attendance sheet. */
    ATTENDANCE_NOT_CONFIRMED;

    private static final Set<IssueCode> ERROR_CODES = Set.of(
            INVALID_ATTENDANCE_CODE, MISSING_CONTRACT_RATE,
            RECON_TOTAL_MISMATCH, RECON_BRANCH_MISMATCH, RECON_PAYSLIP_MISMATCH);

    public boolean isAlwaysError() {
        return ERROR_CODES.contains(this);
    }
}

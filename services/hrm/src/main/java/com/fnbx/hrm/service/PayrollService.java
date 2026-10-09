package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import java.util.List;
import java.util.UUID;

/** The signed-in employee's own payroll - always scoped to the caller's staff id, never a permission check. */
public interface PayrollService {

    /** The pay periods the caller has a payroll line in, newest first. */
    List<com.fnbx.hrm.dto.response.OwnPeriodResponse> getOwnPeriods();

    List<PayslipResponse> getOwnPayslips();

    PayslipDetailResponse getOwnPayslip(UUID payslipId);

    TimesheetGridResponse getOwnTimesheet(UUID periodId);

    AnnualLeaveBalanceResponse getOwnAnnualLeaveBalance(short year);
}

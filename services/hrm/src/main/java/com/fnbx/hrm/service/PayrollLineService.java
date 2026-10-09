package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.CreatePayrollLineRequest;
import com.fnbx.hrm.dto.request.UpdatePayrollLineNoteRequest;
import com.fnbx.hrm.dto.response.PayrollLineSummaryResponse;
import com.fnbx.hrm.enums.EmploymentType;
import java.util.List;
import java.util.UUID;

/** A payroll line places one employment assignment at one branch for one payroll period. */
public interface PayrollLineService {

    List<PayrollLineSummaryResponse> listPayrollLines(UUID payrollPeriodId, EmploymentType employmentType, UUID branchId);

    PayrollLineSummaryResponse createPayrollLine(UUID payrollPeriodId, CreatePayrollLineRequest request);

    /** Copies every still-active assignment from another period's lines, skipping duplicates. */
    int copyPayrollLines(UUID payrollPeriodId, UUID sourcePayrollPeriodId);

    PayrollLineSummaryResponse updatePayrollLineNote(UUID payrollLineId, UpdatePayrollLineNoteRequest request);

    void deletePayrollLine(UUID payrollLineId, boolean force);
}

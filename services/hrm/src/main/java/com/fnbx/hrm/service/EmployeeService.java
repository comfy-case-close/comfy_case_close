package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.CreateEmployeeRequest;
import com.fnbx.hrm.dto.request.EmployeeListFilter;
import com.fnbx.hrm.dto.request.TerminateEmployeeRequest;
import com.fnbx.hrm.dto.request.UpdateEmployeeRequest;
import com.fnbx.hrm.dto.response.EmployeeOverviewResponse;
import com.fnbx.hrm.dto.response.EmployeeResponse;
import com.fnbx.hrm.dto.response.EmployeeRestrictedResponse;
import com.fnbx.hrm.dto.response.EmployeeSummaryResponse;
import com.fnbx.hrm.dto.response.PayrollHistoryEntryResponse;
import com.fnbx.shared.utils.PagedResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface EmployeeService {

    PagedResponse<EmployeeSummaryResponse> listEmployees(EmployeeListFilter filter, Pageable pageable);

    EmployeeOverviewResponse overview();

    EmployeeResponse createEmployee(CreateEmployeeRequest request);

    EmployeeResponse getEmployee(UUID staffId);

    EmployeeRestrictedResponse getEmployeeRestricted(UUID branchId, UUID staffId);

    EmployeeResponse updateEmployee(UUID staffId, UpdateEmployeeRequest request);

    /** Termination is a date on the employee, never folded into a status enum (R10). */
    EmployeeResponse terminateEmployee(UUID staffId, TerminateEmployeeRequest request);

    List<PayrollHistoryEntryResponse> getPayrollHistory(UUID staffId);
}

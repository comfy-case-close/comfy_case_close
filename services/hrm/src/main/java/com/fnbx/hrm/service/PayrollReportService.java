package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.BranchRevenueEntryRequest;
import com.fnbx.hrm.dto.response.BranchLaborCostResponse;
import com.fnbx.hrm.dto.response.PayrollDashboardResponse;
import com.fnbx.hrm.dto.response.EmployeePeriodSummaryResponse;
import com.fnbx.hrm.dto.response.ReconciliationCheckResponse;
import com.fnbx.hrm.dto.response.SharedCostAllocationResponse;
import java.util.List;
import java.util.UUID;

public interface PayrollReportService {

    PayrollDashboardResponse getDashboard(UUID periodId);

    List<BranchLaborCostResponse> getBranchLaborCost(UUID periodId);

    List<EmployeePeriodSummaryResponse> getEmployeeSummary(UUID periodId);

    List<SharedCostAllocationResponse> getSharedCostAllocation(UUID periodId);

    List<ReconciliationCheckResponse> getReconciliation(UUID periodId);

    void setBranchRevenues(UUID periodId, List<BranchRevenueEntryRequest> entries);
}

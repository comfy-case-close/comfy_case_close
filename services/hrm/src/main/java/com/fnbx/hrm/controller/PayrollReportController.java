package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.BranchLaborCostResponse;
import com.fnbx.hrm.dto.response.EmployeePeriodSummaryResponse;
import com.fnbx.hrm.dto.response.PayrollDashboardResponse;
import com.fnbx.hrm.dto.response.ReconciliationCheckResponse;
import com.fnbx.hrm.dto.response.SharedCostAllocationResponse;
import com.fnbx.hrm.service.PayrollReportService;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Labor-cost views of a calculated period: totals, per branch, per employee, shared costs, and cross-checks. */
@RestController
@RequestMapping("/payroll-periods/{payrollPeriodId}/reports")
@RequiredArgsConstructor
public class PayrollReportController {

    private final PayrollReportService payrollReportService;

    @GetMapping("/dashboard")
    public ResponseEntity<PayrollDashboardResponse> getDashboard(@PathVariable UUID payrollPeriodId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth fromMonth,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth toMonth) {
        return ResponseEntity.ok(payrollReportService.getDashboard(payrollPeriodId, fromMonth, toMonth));
    }

    @GetMapping("/branch-labor-cost")
    public ResponseEntity<List<BranchLaborCostResponse>> getBranchLaborCost(@PathVariable UUID payrollPeriodId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth fromMonth,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth toMonth) {
        return ResponseEntity.ok(payrollReportService.getBranchLaborCost(payrollPeriodId, fromMonth, toMonth));
    }

    @GetMapping("/employee-summary")
    public ResponseEntity<List<EmployeePeriodSummaryResponse>> getEmployeeSummary(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollReportService.getEmployeeSummary(payrollPeriodId));
    }

    @GetMapping("/shared-cost-allocation")
    public ResponseEntity<List<SharedCostAllocationResponse>> getSharedCostAllocation(
            @PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollReportService.getSharedCostAllocation(payrollPeriodId));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<List<ReconciliationCheckResponse>> getReconciliation(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollReportService.getReconciliation(payrollPeriodId));
    }
}

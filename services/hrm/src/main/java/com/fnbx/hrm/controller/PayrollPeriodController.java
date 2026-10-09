package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.BranchRevenueEntryRequest;
import com.fnbx.hrm.dto.request.CreatePayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UnlockPayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UpdatePeriodDatesRequest;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.dto.response.PayrollPeriodDecisionResponse;
import com.fnbx.hrm.dto.response.PayrollPeriodResponse;
import com.fnbx.hrm.service.PayrollPeriodService;
import com.fnbx.hrm.service.PayrollReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** One month of payroll (what used to be one Excel workbook): DRAFT, then LOCKED, then PAID. */
@RestController
@RequestMapping("/payroll-periods")
@RequiredArgsConstructor
public class PayrollPeriodController {

    private final PayrollPeriodService payrollPeriodService;
    private final PayrollReportService payrollReportService;

    @GetMapping
    public ResponseEntity<List<PayrollPeriodResponse>> listPayrollPeriods(
            @RequestParam(required = false) Short year, @RequestParam(required = false) PeriodStatus status) {
        return ResponseEntity.ok(payrollPeriodService.listPayrollPeriods(year, status));
    }

    @PostMapping
    public ResponseEntity<PayrollPeriodResponse> createPayrollPeriod(
            @Valid @RequestBody CreatePayrollPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollPeriodService.createPayrollPeriod(request));
    }

    @GetMapping("/{payrollPeriodId}")
    public ResponseEntity<PayrollPeriodResponse> getPayrollPeriod(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollPeriodService.getPayrollPeriod(payrollPeriodId));
    }

    @PutMapping("/{payrollPeriodId}/dates")
    public ResponseEntity<PayrollPeriodResponse> updatePeriodDates(@PathVariable UUID payrollPeriodId,
            @Valid @RequestBody UpdatePeriodDatesRequest request) {
        return ResponseEntity.ok(payrollPeriodService.updatePeriodDates(payrollPeriodId, request));
    }

    @PostMapping("/{payrollPeriodId}/lock")
    public ResponseEntity<PayrollPeriodResponse> lockPeriod(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollPeriodService.lockPeriod(payrollPeriodId));
    }

    @PostMapping("/{payrollPeriodId}/unlock")
    public ResponseEntity<PayrollPeriodResponse> unlockPeriod(@PathVariable UUID payrollPeriodId,
            @Valid @RequestBody UnlockPayrollPeriodRequest request) {
        return ResponseEntity.ok(payrollPeriodService.unlockPeriod(payrollPeriodId, request));
    }

    @GetMapping("/{payrollPeriodId}/decisions")
    public ResponseEntity<List<PayrollPeriodDecisionResponse>> listPeriodDecisions(
            @PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollPeriodService.listDecisions(payrollPeriodId));
    }

    @PostMapping("/{payrollPeriodId}/mark-paid")
    public ResponseEntity<PayrollPeriodResponse> markPayrollPeriodPaid(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollPeriodService.markPayrollPeriodPaid(payrollPeriodId));
    }

    @PutMapping("/{payrollPeriodId}/branch-revenues")
    public ResponseEntity<Void> setBranchRevenues(@PathVariable UUID payrollPeriodId,
            @Valid @RequestBody List<BranchRevenueEntryRequest> entries) {
        payrollReportService.setBranchRevenues(payrollPeriodId, entries);
        return ResponseEntity.noContent().build();
    }
}

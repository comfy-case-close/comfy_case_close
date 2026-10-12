package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.PayrollLineCalculationResponse;
import com.fnbx.hrm.dto.response.PayrollLineResponse;
import com.fnbx.hrm.dto.response.PayrollRunResponse;
import com.fnbx.hrm.service.PayrollCalculationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Runs the payroll engine for a period and exposes what it calculated, down to each formula's inputs. */
@RestController
@RequiredArgsConstructor
public class PayrollCalculationController {

    private final PayrollCalculationService payrollCalculationService;

    /** Always 200: a failed run is a normal outcome, reported in the body with its validation issues. */
    @PostMapping("/payroll-periods/{payrollPeriodId}/payroll-runs")
    public ResponseEntity<PayrollRunResponse> runPayroll(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollCalculationService.runPayroll(payrollPeriodId));
    }

    @GetMapping("/payroll-periods/{payrollPeriodId}/payroll-runs")
    public ResponseEntity<List<PayrollRunResponse>> listRuns(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollCalculationService.listRuns(payrollPeriodId));
    }

    @GetMapping("/payroll-runs/{payrollRunId}")
    public ResponseEntity<PayrollRunResponse> getRun(@PathVariable UUID payrollRunId) {
        return ResponseEntity.ok(payrollCalculationService.getRun(payrollRunId));
    }

    @GetMapping("/payroll-periods/{payrollPeriodId}/payroll-table")
    public ResponseEntity<List<PayrollLineResponse>> getPayrollTable(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollCalculationService.getPayrollTable(payrollPeriodId));
    }

    @GetMapping("/payroll-lines/{payrollLineId}/calculation")
    public ResponseEntity<PayrollLineCalculationResponse> getLineCalculation(@PathVariable UUID payrollLineId) {
        return ResponseEntity.ok(payrollCalculationService.getLineCalculation(payrollLineId));
    }
}

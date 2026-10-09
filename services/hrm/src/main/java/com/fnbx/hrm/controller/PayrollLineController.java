package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.CreatePayrollLineRequest;
import com.fnbx.hrm.dto.request.UpdatePayrollLineNoteRequest;
import com.fnbx.hrm.dto.response.PayrollLineSummaryResponse;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.service.PayrollLineService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Decides who is paid at which branch in a period: one line = one employment assignment at one branch. */
@RestController
@RequiredArgsConstructor
public class PayrollLineController {

    private final PayrollLineService payrollLineService;

    @GetMapping("/payroll-periods/{payrollPeriodId}/payroll-lines")
    public ResponseEntity<List<PayrollLineSummaryResponse>> listPayrollLines(@PathVariable UUID payrollPeriodId,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(payrollLineService.listPayrollLines(payrollPeriodId, employmentType, branchId));
    }

    @PostMapping("/payroll-periods/{payrollPeriodId}/payroll-lines")
    public ResponseEntity<PayrollLineSummaryResponse> createPayrollLine(@PathVariable UUID payrollPeriodId,
            @Valid @RequestBody CreatePayrollLineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payrollLineService.createPayrollLine(payrollPeriodId, request));
    }

    @PostMapping("/payroll-periods/{payrollPeriodId}/payroll-lines/copy-from/{sourcePayrollPeriodId}")
    public ResponseEntity<Map<String, Integer>> copyPayrollLines(@PathVariable UUID payrollPeriodId,
            @PathVariable UUID sourcePayrollPeriodId) {
        int copied = payrollLineService.copyPayrollLines(payrollPeriodId, sourcePayrollPeriodId);
        return ResponseEntity.ok(Map.of("copied", copied));
    }

    @PatchMapping("/payroll-lines/{payrollLineId}")
    public ResponseEntity<PayrollLineSummaryResponse> updatePayrollLineNote(@PathVariable UUID payrollLineId,
            @Valid @RequestBody UpdatePayrollLineNoteRequest request) {
        return ResponseEntity.ok(payrollLineService.updatePayrollLineNote(payrollLineId, request));
    }

    @DeleteMapping("/payroll-lines/{payrollLineId}")
    public ResponseEntity<Void> deletePayrollLine(@PathVariable UUID payrollLineId,
            @RequestParam(defaultValue = "false") boolean force) {
        payrollLineService.deletePayrollLine(payrollLineId, force);
        return ResponseEntity.noContent().build();
    }
}

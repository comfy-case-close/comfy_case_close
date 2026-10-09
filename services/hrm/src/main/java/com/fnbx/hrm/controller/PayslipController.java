package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.SendPayslipsRequest;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipEmailLogResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.service.PayslipConfirmationService;
import com.fnbx.hrm.service.PayslipService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** One consolidated payslip per employee per period: generate it, read it, email it, audit the emails. */
@RestController
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService payslipService;
    private final PayslipConfirmationService confirmationService;

    @PostMapping("/payroll-periods/{payrollPeriodId}/payslips/generate")
    public ResponseEntity<List<PayslipResponse>> generate(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payslipService.generate(payrollPeriodId));
    }

    @GetMapping("/payroll-periods/{payrollPeriodId}/payslips")
    public ResponseEntity<List<PayslipResponse>> listByPayrollPeriod(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(payslipService.list(payrollPeriodId));
    }

    @GetMapping("/payslips/{payslipId}")
    public ResponseEntity<PayslipDetailResponse> get(@PathVariable UUID payslipId) {
        return ResponseEntity.ok(payslipService.get(payslipId));
    }

    @PostMapping("/payroll-periods/{payrollPeriodId}/payslips/send")
    public ResponseEntity<Void> sendByPayrollPeriod(@PathVariable UUID payrollPeriodId,
            @RequestBody(required = false) SendPayslipsRequest request) {
        payslipService.send(payrollPeriodId, request == null ? new SendPayslipsRequest() : request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/payslips/{payslipId}/send")
    public ResponseEntity<Void> send(@PathVariable UUID payslipId) {
        payslipService.sendOne(payslipId);
        return ResponseEntity.accepted().build();
    }

    /** No PDF renderer has been chosen yet - see EMAIL_AND_STORAGE_IMPLEMENTATION_PLAN.md. */
    @GetMapping("/payslips/{payslipId}/pdf")
    public ResponseEntity<Void> getPdf(@PathVariable UUID payslipId) {
        throw PayrollExceptions.featureNotAvailable("Payslip PDF rendering is not implemented yet");
    }

    @PostMapping("/payslips/{payslipId}/confirmation/resend")
    public ResponseEntity<Void> resendConfirmation(@PathVariable UUID payslipId) {
        confirmationService.resend(payslipId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/payroll-periods/{payrollPeriodId}/payslips/confirmation-reminders")
    public ResponseEntity<Integer> remindUnconfirmed(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(confirmationService.remindPending(payrollPeriodId));
    }

    @GetMapping("/payslips/{payslipId}/email-logs")
    public ResponseEntity<List<PayslipEmailLogResponse>> listEmailLogs(@PathVariable UUID payslipId) {
        return ResponseEntity.ok(payslipService.listEmailLogs(payslipId));
    }
}

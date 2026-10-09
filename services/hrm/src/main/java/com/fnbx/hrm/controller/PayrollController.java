package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.service.PayrollService;
import com.fnbx.hrm.service.PayslipConfirmationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The signed-in employee's own payroll: payslips, timesheet, annual leave.
 * Every endpoint is scoped to the caller's staff id, so no permission is needed.
 */
@RestController
@RequestMapping("/own")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final PayslipConfirmationService confirmationService;

    @GetMapping("/payroll-periods")
    public ResponseEntity<List<com.fnbx.hrm.dto.response.OwnPeriodResponse>> getOwnPeriods() {
        return ResponseEntity.ok(payrollService.getOwnPeriods());
    }

    @GetMapping("/payslips")
    public ResponseEntity<List<PayslipResponse>> getOwnPayslips() {
        return ResponseEntity.ok(payrollService.getOwnPayslips());
    }

    @GetMapping("/payslips/{payslipId}")
    public ResponseEntity<PayslipDetailResponse> getOwnPayslip(@PathVariable UUID payslipId) {
        return ResponseEntity.ok(payrollService.getOwnPayslip(payslipId));
    }

    @PostMapping("/payslips/{payslipId}/confirm")
    public ResponseEntity<Void> confirmOwnPayslip(@PathVariable UUID payslipId, HttpServletRequest request) {
        confirmationService.confirmOwn(payslipId, ClientInfoExtractor.from(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payslips/{payslipId}/pdf")
    public ResponseEntity<Void> getOwnPayslipPdf(@PathVariable UUID payslipId) {
        throw PayrollExceptions.featureNotAvailable("Payslip PDF rendering is not implemented yet");
    }

    @GetMapping("/timesheet")
    public ResponseEntity<TimesheetGridResponse> getOwnTimesheet(@RequestParam UUID payrollPeriodId) {
        return ResponseEntity.ok(payrollService.getOwnTimesheet(payrollPeriodId));
    }

    @GetMapping("/annual-leave-balance")
    public ResponseEntity<AnnualLeaveBalanceResponse> getOwnAnnualLeaveBalance(@RequestParam short year) {
        return ResponseEntity.ok(payrollService.getOwnAnnualLeaveBalance(year));
    }
}

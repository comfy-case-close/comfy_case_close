package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.FailPaymentRequest;
import com.fnbx.hrm.dto.request.MarkPaidRequest;
import com.fnbx.hrm.dto.request.PaymentFilter;
import com.fnbx.hrm.dto.response.BankAccountResponse;
import com.fnbx.hrm.dto.response.PaymentListResponse;
import com.fnbx.hrm.dto.response.PaymentResponse;
import com.fnbx.hrm.enums.PaymentStatus;
import com.fnbx.hrm.service.PayrollPaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The accountant's page: who still has to be paid, with the bank details needed to transfer by hand. */
@RestController
@RequestMapping("/payroll-payments")
@RequiredArgsConstructor
public class PayrollPaymentController {

    private final PayrollPaymentService paymentService;

    @GetMapping
    public ResponseEntity<PaymentListResponse> list(@RequestParam UUID periodId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(paymentService.list(new PaymentFilter(periodId, status, bankCode, branchId, search)));
    }

    @PostMapping("/mark-paid")
    public ResponseEntity<List<PaymentResponse>> markPaid(@Valid @RequestBody MarkPaidRequest request) {
        return ResponseEntity.ok(paymentService.markPaid(request));
    }

    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID paymentId, @Valid @RequestBody FailPaymentRequest request) {
        return ResponseEntity.ok(paymentService.fail(paymentId, request));
    }

    @GetMapping("/{paymentId}/bank-account")
    public ResponseEntity<BankAccountResponse> bankAccount(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.readBankAccount(paymentId));
    }
}

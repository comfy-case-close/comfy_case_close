package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID payrollPaymentId,
        UUID payslipId,
        UUID staffId,
        String employeeCode,
        String employeeName,
        BigDecimal amount,
        String bankCode,
        String bankName,
        String bankAccountMasked,
        String bankAccountName,
        String transferNote,
        String status,
        Instant paidAt,
        String bankReference,
        String failureReason,
        boolean missingBankInfo) {
}

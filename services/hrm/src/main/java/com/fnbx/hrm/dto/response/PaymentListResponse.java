package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PaymentListResponse(
        List<PaymentResponse> items,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        long missingBankInfoCount,
        boolean readyToMarkPeriodPaid) {
}

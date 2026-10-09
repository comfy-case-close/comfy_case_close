package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmationViewResponse(
        int periodMonth,
        int periodYear,
        BigDecimal netTotal,
        String bankName,
        String bankAccountMasked,
        String status,
        Instant confirmedAt) {
}

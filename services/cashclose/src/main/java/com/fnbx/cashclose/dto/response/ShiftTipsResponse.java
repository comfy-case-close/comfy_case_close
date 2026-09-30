package com.fnbx.cashclose.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tips received in one branch, business date and shift. Amounts are gross tips. */
public record ShiftTipsResponse(
        UUID cashCloseId,
        UUID branchId,
        UUID shiftTypeId,
        LocalDate businessDate,
        String cashCloseStatus,
        BigDecimal totalTips,
        BigDecimal pendingTips,
        int pendingCount,
        List<CashMovementResponse> movements) {}

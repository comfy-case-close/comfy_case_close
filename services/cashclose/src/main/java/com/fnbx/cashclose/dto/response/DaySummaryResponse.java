package com.fnbx.cashclose.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Independent shift calculations for a branch/day, not a cumulative drawer balance. */
public record DaySummaryResponse(UUID branchId, LocalDate businessDate, List<CashCloseResponse> closes) {}

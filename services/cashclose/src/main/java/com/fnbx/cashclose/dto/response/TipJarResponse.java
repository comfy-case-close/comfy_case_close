package com.fnbx.cashclose.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Branch-scoped tip jar, in the same shape as dev's tip-jar read model. */
public record TipJarResponse(Scope scope, Summary summary, List<Payout> payouts) {
    public record Scope(UUID branchId, String branchCode, String branchLabel,
                        LocalDate fromDate, LocalDate toDate) {}

    public record Summary(BigDecimal tipsIn, BigDecimal tipsInsideDrawer,
                          BigDecimal paidOut, BigDecimal balance, long payoutCount) {}

    public record Payout(UUID id, UUID branchId, String branchCode, String branchName,
                         BigDecimal amount, LocalDate payoutDate, String recipientName,
                         String note, UUID createdById, String createdByName, Instant createdAt) {}
}

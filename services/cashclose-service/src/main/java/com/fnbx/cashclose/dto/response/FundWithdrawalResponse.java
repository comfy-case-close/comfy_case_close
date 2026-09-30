package com.fnbx.cashclose.dto.response;

import com.fnbx.cashclose.entity.FundWithdrawal;
import com.fnbx.cashclose.enums.CashPot;
import com.fnbx.cashclose.enums.FundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundWithdrawalResponse(UUID id, String code, UUID transferId, int revision,
        UUID supersedesId, UUID cashCloseId, UUID branchId, CashPot fromPot, CashPot toPot,
        BigDecimal amount, UUID withdrawnBy, Instant withdrawnAt, UUID recordedBy, Instant createdAt,
        FundStatus status, UUID confirmedBy, Instant confirmedAt, UUID rejectedBy, Instant rejectedAt,
        String rejectionReason, Instant supersededAt, String editReason, String note,
        @lombok.With java.util.List<Warning> warnings) {
    public record Warning(String code, BigDecimal amount, BigDecimal limit) {}
    public static FundWithdrawalResponse from(FundWithdrawal w) {
        return new FundWithdrawalResponse(w.getFundWithdrawalId(), w.getFundWithdrawalCode(),
                w.getTransferId(), w.getRevision(), w.getSupersedesId(), w.getCashCloseId(), w.getBranchId(),
                w.getFromPot(), w.getToPot(), w.getAmount(), w.getWithdrawnBy(), w.getWithdrawnAt(),
                w.getRecordedBy(), w.getCreatedAt(), w.getStatus(), w.getConfirmedBy(), w.getConfirmedAt(),
                w.getRejectedBy(), w.getRejectedAt(), w.getRejectionReason(), w.getSupersededAt(),
                w.getEditReason(), w.getNote(), java.util.List.of());
    }
}

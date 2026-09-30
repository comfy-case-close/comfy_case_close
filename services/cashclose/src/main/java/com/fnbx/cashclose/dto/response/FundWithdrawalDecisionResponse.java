package com.fnbx.cashclose.dto.response;

import com.fnbx.cashclose.entity.FundWithdrawalDecision;
import com.fnbx.cashclose.enums.FundStatus;
import com.fnbx.cashclose.enums.FundWithdrawalAction;
import java.time.Instant;
import java.util.UUID;

public record FundWithdrawalDecisionResponse(UUID decisionId, UUID fundWithdrawalId,
        FundWithdrawalAction action, UUID actedBy, Instant actedAt,
        FundStatus oldStatus, FundStatus newStatus, String note, java.util.Map<String, Object> changes) {
    public static FundWithdrawalDecisionResponse from(FundWithdrawalDecision d) {
        return new FundWithdrawalDecisionResponse(d.getDecisionId(), d.getFundWithdrawalId(),
                d.getAction(), d.getActedBy(), d.getActedAt(), d.getOldStatus(), d.getNewStatus(), d.getNote(), d.getChanges());
    }
}

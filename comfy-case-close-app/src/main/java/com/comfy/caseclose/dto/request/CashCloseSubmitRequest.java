package com.comfy.caseclose.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CashCloseSubmitRequest {

    @NotNull(message = "Branch id is required")
    private Long branchId;

    @NotNull(message = "Shift type id is required")
    private Long shiftTypeId;

    @NotNull(message = "Business date is required")
    private LocalDate businessDate;

    @NotNull(message = "POS expected cash is required")
    @PositiveOrZero(message = "POS expected cash must be zero or positive")
    private Long posExpectedCash;

    @NotNull(message = "Counted cash is required")
    @PositiveOrZero(message = "Counted cash must be zero or positive")
    private Long countedCash;

    @NotNull(message = "Withdrawal amount is required")
    @PositiveOrZero(message = "Withdrawal amount must be zero or positive")
    private Long withdrawalAmount;

    @Valid
    private List<CashDenominationRequest> denominations;

    @Valid
    private List<CashMovementRequest> movements;

    @Valid
    private List<CashDiffExplanationRequest> explanations;

    @Valid
    private TipRequest tips;

    @Valid
    private List<AttachmentRequest> attachments;

    private String note;
}

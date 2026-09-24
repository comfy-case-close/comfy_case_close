package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TipPayoutRequest {

    @NotNull(message = "Branch id is required")
    private Long branchId;

    @NotNull(message = "Payout amount is required")
    @Positive(message = "Payout amount must be greater than zero")
    private Long amount;

    private LocalDate payoutDate;

    @Size(max = 100, message = "Recipient name must be at most 100 characters")
    private String recipientName;

    private String note;
}

package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class TipRequest {

    @NotNull(message = "Tip amount is required")
    @PositiveOrZero(message = "Tip amount must be zero or positive")
    private Long amount;

    private Boolean isInsideCashDrawer = false;

    private String note;
}

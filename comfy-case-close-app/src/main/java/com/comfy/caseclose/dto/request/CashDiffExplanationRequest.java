package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashDiffExplanationRequest {

    @NotBlank(message = "Reason is required")
    private String reason;  // DiffReasonType enum value

    @NotNull(message = "Signed amount is required")
    private Long signedAmount;  // VND, can be negative

    private String notes;
}

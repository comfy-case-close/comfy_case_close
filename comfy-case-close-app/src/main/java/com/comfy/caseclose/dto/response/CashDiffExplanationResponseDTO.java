package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashDiffExplanationResponseDTO {
    private Long id;
    private String reason;  // DiffReasonType
    private Long signedAmount;  // VND, can be negative
    private Long amount;  // Computed: ABS(signedAmount)
    private String direction;  // Computed: signedAmount > 0 ? OVER : SHORT
    private String notes;
    private Long cashCloseId;
}

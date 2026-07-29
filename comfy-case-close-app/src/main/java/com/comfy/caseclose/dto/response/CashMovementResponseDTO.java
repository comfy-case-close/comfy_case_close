package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashMovementResponseDTO {
    private Long id;
    private String category;  // MovementCategory
    private String type;  // MovementType
    private Long amount;  // VND
    private String reason;  // DiffReasonType
    private String description;
    private Long lineTotal;  // Computed: category == EXPENSE && type == EXPENSE ? amount : 0
    private Boolean affectsDiff;  // Computed: type == EXPENSE
    private Long cashCloseId;
}

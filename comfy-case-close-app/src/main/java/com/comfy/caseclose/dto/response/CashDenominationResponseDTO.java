package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashDenominationResponseDTO {

    private Long id;
    private Long cashCloseId;
    private Long denominationValue;
    private Integer quantity;
    private Long lineTotal;
}

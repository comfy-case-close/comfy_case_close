package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class TipResponseDTO {

    private Long id;
    private Long cashCloseId;
    private Long amount;
    private Boolean isInsideCashDrawer;
    private String note;
    private OffsetDateTime createdAt;
}

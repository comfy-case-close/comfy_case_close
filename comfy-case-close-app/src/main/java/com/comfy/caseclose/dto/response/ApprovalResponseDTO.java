package com.comfy.caseclose.dto.response;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalResponseDTO {
    private Long id;
    private String action;  // ApprovalAction (APPROVE, REJECT, AUTO_APPROVED)
    private String reason;
    private Long approvedByUserId;
    private String approvedByName;
    private OffsetDateTime approvedAt;
    private Long cashCloseId;
}

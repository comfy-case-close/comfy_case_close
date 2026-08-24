package com.comfy.caseclose.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponseDTO {
    private Long id;
    private String channel;  // AlertChannel (EMAIL, IN_APP)
    private String severity;  // AlertSeverity (LOW, MEDIUM, HIGH, CRITICAL)
    private String status;  // AlertStatus (ACTIVE, RESOLVED, DISMISSED)
    private String message;
    private Long cashCloseId;  // nullable
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;  // nullable
    private List<Long> recipientUserIds;
}

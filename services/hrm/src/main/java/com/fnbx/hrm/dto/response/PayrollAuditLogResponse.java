package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollAuditLogResponse {
    private Long logId;
    private Instant occurredAt;
    private UUID actorUserId;
    private String action;
    private String entityType;
    private String entityId;
    private String oldValue;
    private String newValue;
}

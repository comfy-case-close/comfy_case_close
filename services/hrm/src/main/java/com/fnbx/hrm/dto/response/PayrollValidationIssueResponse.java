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
public class PayrollValidationIssueResponse {
    private UUID dataValidationIssueId;
    private String issueCode;
    private String severity;
    private String entityRef;
    private String message;
    private boolean acknowledged;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
    private String acknowledgeNote;
    private Instant detectedAt;
}

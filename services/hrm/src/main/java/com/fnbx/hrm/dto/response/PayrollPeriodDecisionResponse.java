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
public class PayrollPeriodDecisionResponse {
    private UUID payrollPeriodDecisionId;
    private String action;
    private String oldStatus;
    private String newStatus;
    private UUID actedBy;
    private String actedPermission;
    private String reason;
    private Instant actedAt;
}

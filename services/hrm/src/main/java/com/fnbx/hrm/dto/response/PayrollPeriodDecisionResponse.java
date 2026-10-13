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
    /** Who acted, for display; null if the person no longer exists. */
    private String actedByName;
    private String actedPermission;
    private String reason;
    private Instant actedAt;
}

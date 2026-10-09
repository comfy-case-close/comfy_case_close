package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRunResponse {
    private UUID payrollRunId;
    private UUID periodId;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer linesCalculated;
    private List<PayrollValidationIssueResponse> issues;
}

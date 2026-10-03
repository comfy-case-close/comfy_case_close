package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodResponse {
    private UUID payrollPeriodId;
    private short periodYear;
    private short periodMonth;
    private UUID configId;
    private LocalDate startDate;
    private LocalDate endDate;
    private short daysInPeriod;
    private String status;
    private Instant timesheetModifiedAt;
    private UUID lockedBy;
    private Instant lockedAt;
    private long version;
    /** Summary counters for the period screen (spec E40): line count, open issues, staleness. */
    private long lineCount;
    private long invalidCellCount;
    private long openIssueCount;
    private boolean stale;
}

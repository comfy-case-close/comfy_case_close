package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleSummaryResponse(UUID shiftScheduleId, UUID branchId, LocalDate weekStart, String status,
                                      long version, Instant submittedAt, Instant approvedAt) {
}

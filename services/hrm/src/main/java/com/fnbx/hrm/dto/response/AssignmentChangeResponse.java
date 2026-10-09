package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssignmentChangeResponse(UUID shiftAssignmentId, UUID staffId, String nickname, LocalDate workDate,
                                       String eventType, UUID actorId, String before, String after, String reason,
                                       Instant occurredAt) {
}

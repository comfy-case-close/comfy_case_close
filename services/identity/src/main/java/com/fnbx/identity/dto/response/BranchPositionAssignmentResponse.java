package com.fnbx.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BranchPositionAssignmentResponse(UUID positionId, Instant assignedAt, Instant revokedAt) {
}

package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** One person offered for a shift; {@code selectable} is false only when they cannot be added at all. */
public record CandidateResponse(UUID staffId, String nickname, String fullName, String employmentType, String status,
                                boolean selectable, String note, BigDecimal assignedHours, BigDecimal missingHours) {
}

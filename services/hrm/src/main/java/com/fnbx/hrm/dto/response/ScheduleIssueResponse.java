package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleIssueResponse(String type, LocalDate date, UUID staffId, String nickname, UUID positionId, String detail) {
}

package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AttendanceRequests {

    private AttendanceRequests() {}

    /** Marks one shift. {@code lateLevel} is required for LATE and must be absent otherwise. */
    public record Mark(@NotNull AttendanceExceptionStatus status, LateLevel lateLevel, String note) {}

    public record Transition(@NotNull Long expectedVersion) {}

    public record Return(@NotBlank String reason, @NotNull Long expectedVersion) {}
}

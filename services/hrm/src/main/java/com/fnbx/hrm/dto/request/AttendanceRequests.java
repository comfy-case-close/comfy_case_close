package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class AttendanceRequests {

    private AttendanceRequests() {}

    public record Mark(@NotNull AttendanceExceptionStatus status, LateLevel lateLevel, String note, Boolean excused,
                       BigDecimal adjustedHours) {

        public Mark(AttendanceExceptionStatus status, LateLevel lateLevel, String note) {
            this(status, lateLevel, note, null, null);
        }

        public boolean isExcused() {
            return Boolean.TRUE.equals(excused);
        }
    }

    public record Transition(@NotNull Long expectedVersion) {}

    public record Return(@NotBlank String reason, @NotNull Long expectedVersion) {}
}

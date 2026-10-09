package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.AttendanceException;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** What one scheduled shift turned out to be: worked on time unless the attendance sheet records an exception. */
public record ShiftOutcome(BigDecimal scheduledHours, AttendanceExceptionStatus exception, LateLevel lateLevel) {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    public static ShiftOutcome of(BigDecimal scheduledHours, AttendanceException exception) {
        return exception == null ? new ShiftOutcome(scheduledHours, null, null)
                : new ShiftOutcome(scheduledHours, exception.getStatus(), exception.getLateLevel());
    }

    public boolean onTime() {
        return exception == null;
    }

    public boolean late() {
        return exception == AttendanceExceptionStatus.LATE;
    }

    /** Hours of work this shift contributes to the day; paid leave counts through {@link #payableHours()} instead. */
    public BigDecimal workedHours() {
        if (exception == null) {
            return scheduledHours;
        }
        if (exception == AttendanceExceptionStatus.LATE && lateLevel == LateLevel.LEVEL_1) {
            return scheduledHours.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal payableHours() {
        return exception == AttendanceExceptionStatus.LEAVE_PAID ? scheduledHours : workedHours();
    }
}

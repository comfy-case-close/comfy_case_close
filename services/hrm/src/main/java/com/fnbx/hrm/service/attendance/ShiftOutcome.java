package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.AttendanceException;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import java.math.BigDecimal;

public record ShiftOutcome(BigDecimal scheduledHours, AttendanceExceptionStatus exception, LateLevel lateLevel, LatePenalty penalty,
                           boolean excused, BigDecimal adjustedHours) {

    public ShiftOutcome(BigDecimal scheduledHours, AttendanceExceptionStatus exception, LateLevel lateLevel, LatePenalty penalty) {
        this(scheduledHours, exception, lateLevel, penalty, false, null);
    }

    public ShiftOutcome(BigDecimal scheduledHours, AttendanceExceptionStatus exception, LateLevel lateLevel) {
        this(scheduledHours, exception, lateLevel, lateLevel == null ? null : LatePenalties.defaults().of(lateLevel));
    }

    public static ShiftOutcome of(BigDecimal scheduledHours, AttendanceException exception, LatePenalties penalties) {
        if (exception == null) {
            return new ShiftOutcome(scheduledHours, null, null, null);
        }
        LateLevel level = exception.getLateLevel();
        boolean deducts = exception.getStatus() == AttendanceExceptionStatus.LATE && level != null && !exception.isExcused();
        return new ShiftOutcome(scheduledHours, exception.getStatus(), level, deducts ? penalties.of(level) : null,
                exception.isExcused(), exception.getAdjustedHours());
    }

    public static ShiftOutcome of(BigDecimal scheduledHours, AttendanceException exception) {
        return of(scheduledHours, exception, LatePenalties.defaults());
    }

    public boolean onTime() {
        return exception == null;
    }

    public boolean late() {
        return exception == AttendanceExceptionStatus.LATE && !excused;
    }

    public String lateCode() {
        return penalty == null ? null : penalty.ruleCode();
    }

    public BigDecimal workedHours() {
        if (exception == null) {
            return scheduledHours;
        }
        if (exception == AttendanceExceptionStatus.LATE) {
            return excused || penalty == null ? scheduledHours : penalty.workedHours(scheduledHours);
        }
        if (exception == AttendanceExceptionStatus.ADJUSTED && adjustedHours != null) {
            return adjustedHours;
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal payableHours() {
        return exception == AttendanceExceptionStatus.LEAVE_PAID ? scheduledHours : workedHours();
    }
}

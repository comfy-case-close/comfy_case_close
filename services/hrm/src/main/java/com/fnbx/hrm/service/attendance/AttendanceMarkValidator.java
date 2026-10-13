package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.dto.request.AttendanceRequests;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AttendanceMarkValidator {

    private static final BigDecimal MAX_WORKED_HOURS = BigDecimal.valueOf(24);

    public void validate(AttendanceRequests.Mark mark, SchedulingStaff person, LocalDate workDate) {
        requireExcuseOnlyOnLate(mark);
        requireLateLevelMatchesStatus(mark);
        requireAdjustedHoursMatchStatus(mark);
        requireLeaveAllowed(mark, person, workDate);
    }

    private void requireExcuseOnlyOnLate(AttendanceRequests.Mark mark) {
        if (mark.isExcused() && mark.status() != AttendanceExceptionStatus.LATE) {
            throw PayrollExceptions.invalidField("Only a late shift can be excused");
        }
    }

    private void requireLateLevelMatchesStatus(AttendanceRequests.Mark mark) {
        boolean late = mark.status() == AttendanceExceptionStatus.LATE;
        if (late && mark.isExcused()) {
            requireExcusedLateShape(mark);
        } else if (late != (mark.lateLevel() != null)) {
            throw PayrollExceptions.invalidField("A late level is required for a late shift and not allowed for anything else");
        }
    }

    private void requireExcusedLateShape(AttendanceRequests.Mark mark) {
        if (mark.lateLevel() != null) {
            throw PayrollExceptions.invalidField("A late shift with permission has no late level, because nothing is deducted");
        }
        if (!hasNote(mark)) {
            throw PayrollExceptions.invalidField("A note saying who allowed it is required for a late shift with permission");
        }
    }

    private void requireAdjustedHoursMatchStatus(AttendanceRequests.Mark mark) {
        boolean adjusted = mark.status() == AttendanceExceptionStatus.ADJUSTED;
        if (adjusted != (mark.adjustedHours() != null)) {
            throw PayrollExceptions.invalidField("Worked hours are required for an adjusted shift and not allowed for anything else");
        }
        if (!adjusted) {
            return;
        }
        if (mark.adjustedHours().signum() < 0 || mark.adjustedHours().compareTo(MAX_WORKED_HOURS) > 0) {
            throw PayrollExceptions.invalidField("Worked hours must be between 0 and 24");
        }
        if (!hasNote(mark)) {
            throw PayrollExceptions.invalidField("A note saying why the hours differ is required");
        }
    }

    private void requireLeaveAllowed(AttendanceRequests.Mark mark, SchedulingStaff person, LocalDate workDate) {
        if (mark.status() == AttendanceExceptionStatus.LEAVE_UNPAID) {
            throw PayrollExceptions.invalidField("Only annual leave exists; unpaid leave is not offered");
        }
        boolean fullTime = person != null && person.isFullTimeOn(workDate);
        if (mark.status() == AttendanceExceptionStatus.LEAVE_PAID && !fullTime) {
            throw PayrollExceptions.attendanceLeaveNotAllowed();
        }
    }

    private boolean hasNote(AttendanceRequests.Mark mark) {
        return mark.note() != null && !mark.note().isBlank();
    }
}

package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns the shifts of one person-day into the cell value the payroll timesheet understands. */
@Component
public class DayCellBuilder {

    private static final String PAID_LEAVE = "CP";
    private static final String UNPAID_LEAVE = "KL";
    private static final String LATE_HALF = "T2-";
    private static final String LATE_VOID = "T3-";

    public TimesheetDayCell build(List<ShiftOutcome> shifts) {
        int lateShifts = (int) shifts.stream().filter(ShiftOutcome::late).count();
        BigDecimal payable = shifts.stream().map(ShiftOutcome::payableHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allMatch(shifts, AttendanceExceptionStatus.LEAVE_PAID)) {
            return new TimesheetDayCell(PAID_LEAVE, lateShifts, payable);
        }
        if (allMatch(shifts, AttendanceExceptionStatus.LEAVE_UNPAID)) {
            return new TimesheetDayCell(UNPAID_LEAVE, lateShifts, payable);
        }
        BigDecimal worked = shifts.stream().map(ShiftOutcome::workedHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shifts.size() == 1 && shifts.get(0).late()) {
            ShiftOutcome only = shifts.get(0);
            String prefix = only.lateLevel() == LateLevel.LEVEL_1 ? LATE_HALF : LATE_VOID;
            return new TimesheetDayCell(prefix + format(only.scheduledHours()), lateShifts, payable);
        }
        return worked.signum() == 0 ? new TimesheetDayCell(null, lateShifts, payable) : new TimesheetDayCell(format(worked), lateShifts, payable);
    }

    private boolean allMatch(List<ShiftOutcome> shifts, AttendanceExceptionStatus status) {
        return !shifts.isEmpty() && shifts.stream().allMatch(shift -> shift.exception() == status);
    }

    private String format(BigDecimal hours) {
        return hours.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

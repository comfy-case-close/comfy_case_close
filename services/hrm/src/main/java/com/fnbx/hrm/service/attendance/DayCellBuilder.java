package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DayCellBuilder {

    private static final String PAID_LEAVE = "CP";
    private static final String UNPAID_ABSENCE = "KL";

    public TimesheetDayCell build(List<ShiftOutcome> shifts) {
        int lateShifts = (int) shifts.stream().filter(ShiftOutcome::late).count();
        BigDecimal payable = sum(shifts, ShiftOutcome::payableHours);
        if (allShiftsAre(shifts, AttendanceExceptionStatus.LEAVE_PAID)) {
            return new TimesheetDayCell(PAID_LEAVE, lateShifts, payable);
        }
        if (isUnpaidAbsence(shifts)) {
            return new TimesheetDayCell(UNPAID_ABSENCE, lateShifts, payable);
        }
        if (isSingleLateShiftWithRule(shifts)) {
            ShiftOutcome only = shifts.get(0);
            return new TimesheetDayCell(only.lateCode() + "-" + format(only.scheduledHours()), lateShifts, payable);
        }
        BigDecimal worked = sum(shifts, ShiftOutcome::workedHours);
        return new TimesheetDayCell(worked.signum() == 0 ? null : format(worked), lateShifts, payable);
    }

    private boolean isUnpaidAbsence(List<ShiftOutcome> shifts) {
        return !shifts.isEmpty() && shifts.stream().allMatch(shift -> shift.exception() == AttendanceExceptionStatus.ABSENT
                || shift.exception() == AttendanceExceptionStatus.LEAVE_UNPAID);
    }

    private boolean isSingleLateShiftWithRule(List<ShiftOutcome> shifts) {
        return shifts.size() == 1 && shifts.get(0).late() && shifts.get(0).lateCode() != null;
    }

    private boolean allShiftsAre(List<ShiftOutcome> shifts, AttendanceExceptionStatus status) {
        return !shifts.isEmpty() && shifts.stream().allMatch(shift -> shift.exception() == status);
    }

    private BigDecimal sum(List<ShiftOutcome> shifts, java.util.function.Function<ShiftOutcome, BigDecimal> hours) {
        return shifts.stream().map(hours).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String format(BigDecimal hours) {
        return hours.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

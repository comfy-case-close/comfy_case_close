package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.dto.response.AttendanceResponses.DayCell;
import com.fnbx.hrm.dto.response.AttendanceResponses.Row;
import com.fnbx.hrm.dto.response.AttendanceResponses.Sheet;
import com.fnbx.hrm.dto.response.AttendanceResponses.ShiftLine;
import com.fnbx.hrm.entity.AttendanceException;
import com.fnbx.hrm.entity.AttendanceSheet;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceViewAssembler {

    private final AttendancePlanner planner;
    private final DayCellBuilder cellBuilder;
    private final LateRulebook rulebook;

    public Sheet assemble(AttendanceSheet sheet, ScheduleContext context, List<AttendanceException> exceptions) {
        Map<UUID, AttendanceException> exceptionByAssignment = exceptions.stream()
                .collect(Collectors.toMap(AttendanceException::getShiftAssignmentId, Function.identity()));
        Function<LocalDate, LatePenalties> penaltiesOn = rulebook.cachedByDate();
        List<Row> rows = context.assignments().stream().collect(Collectors.groupingBy(ShiftAssignment::getStaffId)).entrySet().stream()
                .map(staff -> row(context, staff.getKey(), staff.getValue(), exceptionByAssignment, penaltiesOn))
                .sorted(Comparator.comparing(Row::nickname, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new Sheet(sheet.getAttendanceSheetId(), sheet.getShiftScheduleId(), context.schedule().getBranchId(),
                context.schedule().getWeekStart(), sheet.getStatus().name(), sheet.getVersion(), sheet.getSubmittedAt(),
                sheet.getConfirmedAt(), sheet.getReturnReason(), rows, null);
    }

    private Row row(ScheduleContext context, UUID staffId, List<ShiftAssignment> shifts,
            Map<UUID, AttendanceException> exceptions, Function<LocalDate, LatePenalties> penaltiesOn) {
        SchedulingStaff person = context.person(staffId);
        LocalDate weekStart = context.schedule().getWeekStart();
        List<DayCell> days = WeekCalendar.days(weekStart).stream()
                .map(date -> day(context, date, shiftsOn(shifts, date), exceptions, penaltiesOn))
                .toList();
        return new Row(staffId, person == null ? null : person.displayName(),
                person == null ? null : person.typeOn(weekStart).map(Enum::name).orElse(null), days,
                days.stream().map(DayCell::payableHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().mapToInt(DayCell::lateShifts).sum());
    }

    private DayCell day(ScheduleContext context, LocalDate date, List<ShiftAssignment> shifts,
            Map<UUID, AttendanceException> exceptions, Function<LocalDate, LatePenalties> penaltiesOn) {
        List<ShiftOutcome> outcomes = planner.outcomes(shifts, exceptions, penaltiesOn);
        TimesheetDayCell cell = cellBuilder.build(outcomes);
        BigDecimal scheduled = outcomes.stream().map(ShiftOutcome::scheduledHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ShiftLine> lines = shifts.stream().sorted(Comparator.comparing(ShiftAssignment::getStartTime))
                .map(shift -> line(context, shift, exceptions.get(shift.getShiftAssignmentId()), penaltiesOn.apply(date)))
                .toList();
        return new DayCell(date, scheduled, cell.payableHours(), cell.lateShifts(), colour(outcomes), lines);
    }

    private ShiftLine line(ScheduleContext context, ShiftAssignment shift, AttendanceException exception, LatePenalties penalties) {
        ShiftOutcome outcome = ShiftOutcome.of(TimeWindow.of(shift).hours(), exception, penalties);
        String slotName = context.slots().byId(shift.getShiftSlotId()).getName();
        if (exception == null) {
            return new ShiftLine(shift.getShiftAssignmentId(), slotName, shift.getStartTime(), shift.getEndTime(),
                    outcome.scheduledHours(), outcome.payableHours(), "ON_TIME", null, null, false, null);
        }
        return new ShiftLine(shift.getShiftAssignmentId(), slotName, shift.getStartTime(), shift.getEndTime(),
                outcome.scheduledHours(), outcome.payableHours(), exception.getStatus().name(),
                exception.getLateLevel() == null ? null : exception.getLateLevel().name(), exception.getNote(),
                exception.isExcused(), exception.getAdjustedHours());
    }

    private List<ShiftAssignment> shiftsOn(List<ShiftAssignment> shifts, LocalDate date) {
        return shifts.stream().filter(shift -> shift.getWorkDate().equals(date)).toList();
    }

    private String colour(List<ShiftOutcome> outcomes) {
        if (anyShiftIs(outcomes, AttendanceExceptionStatus.ABSENT)) {
            return "ABSENT";
        }
        if (anyShiftIs(outcomes, AttendanceExceptionStatus.LEAVE_PAID) || anyShiftIs(outcomes, AttendanceExceptionStatus.LEAVE_UNPAID)) {
            return "LEAVE";
        }
        return outcomes.stream().anyMatch(ShiftOutcome::late) ? "LATE" : "ON_TIME";
    }

    private boolean anyShiftIs(List<ShiftOutcome> outcomes, AttendanceExceptionStatus status) {
        return outcomes.stream().anyMatch(outcome -> outcome.exception() == status);
    }
}

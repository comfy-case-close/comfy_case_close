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

/** The week grid of an attendance sheet: per person a cell per day with its total and shifts; the cell colour says what happened. */
@Component
@RequiredArgsConstructor
public class AttendanceViewAssembler {

    private final AttendancePlanner planner;
    private final DayCellBuilder cellBuilder;

    public Sheet assemble(AttendanceSheet sheet, ScheduleContext context, List<AttendanceException> exceptions) {
        Map<UUID, AttendanceException> byAssignment = exceptions.stream()
                .collect(Collectors.toMap(AttendanceException::getShiftAssignmentId, Function.identity()));
        Map<UUID, List<ShiftAssignment>> byStaff = context.assignments().stream().collect(Collectors.groupingBy(ShiftAssignment::getStaffId));
        List<Row> rows = byStaff.entrySet().stream()
                .map(entry -> row(context, entry.getKey(), entry.getValue(), byAssignment))
                .sorted(Comparator.comparing(Row::nickname, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new Sheet(sheet.getAttendanceSheetId(), sheet.getShiftScheduleId(), context.schedule().getBranchId(),
                context.schedule().getWeekStart(), sheet.getStatus().name(), sheet.getVersion(), sheet.getSubmittedAt(),
                sheet.getConfirmedAt(), sheet.getReturnReason(), rows);
    }

    private Row row(ScheduleContext context, UUID staffId, List<ShiftAssignment> shifts, Map<UUID, AttendanceException> exceptions) {
        SchedulingStaff person = context.person(staffId);
        List<DayCell> days = WeekCalendar.days(context.schedule().getWeekStart()).stream()
                .map(date -> day(context, date, shifts.stream().filter(shift -> shift.getWorkDate().equals(date)).toList(), exceptions))
                .toList();
        return new Row(staffId, person == null ? null : person.displayName(),
                person == null ? null : person.typeOn(context.schedule().getWeekStart()).map(Enum::name).orElse(null), days,
                days.stream().map(DayCell::payableHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().mapToInt(DayCell::lateShifts).sum());
    }

    private DayCell day(ScheduleContext context, LocalDate date, List<ShiftAssignment> shifts, Map<UUID, AttendanceException> exceptions) {
        List<ShiftOutcome> outcomes = planner.outcomes(shifts, exceptions);
        TimesheetDayCell cell = cellBuilder.build(outcomes);
        BigDecimal scheduled = outcomes.stream().map(ShiftOutcome::scheduledHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DayCell(date, scheduled, cell.payableHours(), cell.lateShifts(), color(outcomes),
                shifts.stream().sorted(Comparator.comparing(ShiftAssignment::getStartTime))
                        .map(shift -> line(context, shift, exceptions.get(shift.getShiftAssignmentId()))).toList());
    }

    private ShiftLine line(ScheduleContext context, ShiftAssignment shift, AttendanceException exception) {
        ShiftOutcome outcome = ShiftOutcome.of(TimeWindow.of(shift).hours(), exception);
        return new ShiftLine(shift.getShiftAssignmentId(), context.slots().byId(shift.getShiftSlotId()).getName(), shift.getStartTime(),
                shift.getEndTime(), outcome.scheduledHours(), outcome.payableHours(),
                exception == null ? "ON_TIME" : exception.getStatus().name(),
                exception == null || exception.getLateLevel() == null ? null : exception.getLateLevel().name(),
                exception == null ? null : exception.getNote());
    }

    private String color(List<ShiftOutcome> outcomes) {
        if (outcomes.stream().anyMatch(o -> o.exception() == AttendanceExceptionStatus.ABSENT)) {
            return "ABSENT";
        }
        if (outcomes.stream().anyMatch(o -> o.exception() == AttendanceExceptionStatus.LEAVE_PAID
                || o.exception() == AttendanceExceptionStatus.LEAVE_UNPAID)) {
            return "LEAVE";
        }
        return outcomes.stream().anyMatch(ShiftOutcome::late) ? "LATE" : "ON_TIME";
    }
}

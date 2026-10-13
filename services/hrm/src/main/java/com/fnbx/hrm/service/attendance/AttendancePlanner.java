package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.AttendanceException;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.service.scheduling.TimeWindow;
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
public class AttendancePlanner {

    public record PlannedCell(UUID staffId, LocalDate date, TimesheetDayCell cell) {}

    private final DayCellBuilder cellBuilder;
    private final LateRulebook rulebook;

    public List<PlannedCell> plan(List<ShiftAssignment> assignments, List<AttendanceException> exceptions) {
        Map<UUID, AttendanceException> exceptionByAssignment = exceptions.stream()
                .collect(Collectors.toMap(AttendanceException::getShiftAssignmentId, Function.identity()));
        Function<LocalDate, LatePenalties> penaltiesOn = rulebook.cachedByDate();
        return shiftsByStaffAndDay(assignments).entrySet().stream()
                .flatMap(staff -> staff.getValue().entrySet().stream()
                        .map(day -> plannedCell(staff.getKey(), day.getKey(), day.getValue(), exceptionByAssignment, penaltiesOn)))
                .sorted(Comparator.comparing(PlannedCell::date).thenComparing(PlannedCell::staffId))
                .toList();
    }

    public List<ShiftOutcome> outcomes(List<ShiftAssignment> shifts, Map<UUID, AttendanceException> exceptions,
            Function<LocalDate, LatePenalties> penaltiesOn) {
        return shifts.stream()
                .map(shift -> ShiftOutcome.of(TimeWindow.of(shift).hours(), exceptions.get(shift.getShiftAssignmentId()),
                        penaltiesOn.apply(shift.getWorkDate())))
                .toList();
    }

    private PlannedCell plannedCell(UUID staffId, LocalDate date, List<ShiftAssignment> shifts,
            Map<UUID, AttendanceException> exceptions, Function<LocalDate, LatePenalties> penaltiesOn) {
        return new PlannedCell(staffId, date, cellBuilder.build(outcomes(shifts, exceptions, penaltiesOn)));
    }

    private Map<UUID, Map<LocalDate, List<ShiftAssignment>>> shiftsByStaffAndDay(List<ShiftAssignment> assignments) {
        return assignments.stream()
                .collect(Collectors.groupingBy(ShiftAssignment::getStaffId, Collectors.groupingBy(ShiftAssignment::getWorkDate)));
    }
}

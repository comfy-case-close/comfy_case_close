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

    public List<PlannedCell> plan(List<ShiftAssignment> assignments, List<AttendanceException> exceptions) {
        Map<UUID, AttendanceException> byAssignment = exceptions.stream()
                .collect(Collectors.toMap(AttendanceException::getShiftAssignmentId, Function.identity()));
        Map<UUID, Map<LocalDate, List<ShiftAssignment>> > byStaffAndDay = assignments.stream()
                .collect(Collectors.groupingBy(ShiftAssignment::getStaffId, Collectors.groupingBy(ShiftAssignment::getWorkDate)));
        return byStaffAndDay.entrySet().stream()
                .flatMap(staff -> staff.getValue().entrySet().stream()
                        .map(day -> new PlannedCell(staff.getKey(), day.getKey(), cellBuilder.build(outcomes(day.getValue(), byAssignment)))))
                .sorted(Comparator.comparing(PlannedCell::date).thenComparing(PlannedCell::staffId))
                .toList();
    }

    public List<ShiftOutcome> outcomes(List<ShiftAssignment> shifts, Map<UUID, AttendanceException> exceptions) {
        return shifts.stream()
                .map(shift -> ShiftOutcome.of(TimeWindow.of(shift).hours(), exceptions.get(shift.getShiftAssignmentId())))
                .toList();
    }
}

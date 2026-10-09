package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.AssignmentView;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.CoverageView;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.DaySlots;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.NeedView;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.SlotView;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.Stats;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.StaffHours;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleViewAssembler {

    private static final int MINUTES_PER_HOUR = 60;

    private final CoverageCalculator coverageCalculator;
    private final SchedulingStaffLoader staffLoader;
    private final SchedulingLimitsProvider limitsProvider;
    private final WeeklyHours weeklyHours;

    public ShiftScheduleResponse assemble(ScheduleContext context) {
        ShiftSchedule schedule = context.schedule();
        List<SegmentCoverage> coverage = coverageCalculator.compute(schedule.getBranchId(), schedule.getWeekStart(),
                context.slots(), context.assignments());
        Map<UUID, String> positionNames = positionNames(context, coverage);
        return new ShiftScheduleResponse(schedule.getShiftScheduleId(), schedule.getBranchId(), schedule.getWeekStart(),
                schedule.getStatus().name(), schedule.getVersion(), schedule.getSubmittedAt(), schedule.getSubmitNote(),
                schedule.getApprovedAt(), schedule.getReturnReason(), days(context), assignments(context, positionNames),
                coverage.stream().map(segment -> coverageView(segment, positionNames)).toList(), stats(context, coverage));
    }

    private List<DaySlots> days(ScheduleContext context) {
        return WeekCalendar.days(context.schedule().getWeekStart()).stream()
                .map(date -> new DaySlots(date, context.slots().on(date).stream()
                        .map(slot -> new SlotView(slot.getShiftSlotId(), slot.getName(), slot.getShiftPeriod().name(),
                                slot.getEmploymentType().name(), slot.getStartTime(), slot.getEndTime()))
                        .toList()))
                .toList();
    }

    private List<AssignmentView> assignments(ScheduleContext context, Map<UUID, String> positionNames) {
        return context.assignments().stream()
                .sorted(Comparator.comparing(ShiftAssignment::getWorkDate).thenComparing(ShiftAssignment::getStartTime))
                .map(assignment -> assignmentView(context, assignment, positionNames))
                .toList();
    }

    public AssignmentView view(ScheduleContext context, ShiftAssignment assignment) {
        return assignmentView(context, assignment, staffLoader.positionNames(List.of(assignment.getPositionId())));
    }

    private AssignmentView assignmentView(ScheduleContext context, ShiftAssignment assignment, Map<UUID, String> positionNames) {
        SchedulingStaff person = context.person(assignment.getStaffId());
        TimeWindow window = TimeWindow.of(assignment);
        return new AssignmentView(assignment.getShiftAssignmentId(), assignment.getStaffId(),
                person == null ? null : person.displayName(), person == null ? null : person.fullName(),
                assignment.getPositionId(), positionNames.get(assignment.getPositionId()), assignment.getWorkDate(),
                assignment.getShiftSlotId(), assignment.getStartTime(), assignment.getEndTime(), window.hours(),
                assignment.getSource().name(),
                context.availability().statusOf(assignment.getStaffId(), assignment.getWorkDate(), window).name(),
                assignment.getNote(), assignment.getVersion());
    }

    private CoverageView coverageView(SegmentCoverage segment, Map<UUID, String> positionNames) {
        List<NeedView> needs = segment.needs().stream()
                .map(need -> new NeedView(need.positionId(), positionNames.get(need.positionId()), need.required(), need.present()))
                .toList();
        return new CoverageView(segment.date(), segment.period().name(), segment.window().start(), segment.window().end(),
                segment.complete(), needs);
    }

    private Stats stats(ScheduleContext context, List<SegmentCoverage> coverage) {
        int seats = coverage.stream().mapToInt(segment -> segment.needs().size()).sum();
        int missing = coverage.stream().mapToInt(segment -> segment.missingPositions().size()).sum();
        BigDecimal ratio = seats == 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(seats - missing).divide(BigDecimal.valueOf(seats), 4, RoundingMode.HALF_UP);
        return new Stats(ratio, missing, belowMinimum(context));
    }

    private List<StaffHours> belowMinimum(ScheduleContext context) {
        Map<UUID, Integer> minimum = limitsProvider.minMinutes(context.limits(), context.staff(), context.schedule().getWeekStart());
        Map<UUID, Integer> worked = weeklyHours.minutesByStaff(context.assignments());
        return context.staff().values().stream()
                .filter(person -> person.typeOn(context.schedule().getWeekStart()).isPresent())
                .filter(person -> worked.getOrDefault(person.staffId(), 0) < minimum.get(person.staffId()))
                .map(person -> new StaffHours(person.staffId(), person.displayName(),
                        toHours(worked.getOrDefault(person.staffId(), 0)), toHours(minimum.get(person.staffId()))))
                .sorted(Comparator.comparing(StaffHours::nickname))
                .toList();
    }

    private Map<UUID, String> positionNames(ScheduleContext context, List<SegmentCoverage> coverage) {
        List<UUID> ids = java.util.stream.Stream.concat(
                context.assignments().stream().map(ShiftAssignment::getPositionId),
                coverage.stream().flatMap(segment -> segment.needs().stream()).map(SegmentCoverage.PositionNeed::positionId))
                .distinct().collect(Collectors.toList());
        return staffLoader.positionNames(ids);
    }

    private BigDecimal toHours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
    }
}

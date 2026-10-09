package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.dto.response.ScheduleIssueResponse;
import com.fnbx.hrm.entity.ShiftAssignment;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The things a store manager should look at before sending the week to the general manager. */
@Component
@RequiredArgsConstructor
public class ScheduleIssueFinder {

    private final CoverageCalculator coverageCalculator;
    private final SchedulingLimitsProvider limitsProvider;
    private final WeeklyHours weeklyHours;

    public List<ScheduleIssueResponse> find(ScheduleContext context) {
        List<ScheduleIssueResponse> issues = new ArrayList<>();
        issues.addAll(missingCoverage(context));
        issues.addAll(availabilityIssues(context));
        issues.addAll(inactiveEmployment(context));
        issues.addAll(belowMinimumHours(context));
        issues.addAll(shortRest(context));
        return issues;
    }

    private List<ScheduleIssueResponse> missingCoverage(ScheduleContext context) {
        List<ScheduleIssueResponse> issues = new ArrayList<>();
        coverageCalculator.compute(context.schedule().getBranchId(), context.schedule().getWeekStart(), context.slots(), context.assignments())
                .forEach(segment -> segment.missingPositions().forEach(positionId -> issues.add(new ScheduleIssueResponse(
                        "MISSING_COVERAGE", segment.date(), null, null, positionId,
                        segment.window().start() + "-" + segment.window().end()))));
        return issues;
    }

    private List<ScheduleIssueResponse> availabilityIssues(ScheduleContext context) {
        List<ScheduleIssueResponse> issues = new ArrayList<>();
        for (ShiftAssignment assignment : context.assignments()) {
            AvailabilityStatus status = context.availability().statusOf(assignment.getStaffId(), assignment.getWorkDate(), TimeWindow.of(assignment));
            if (status != AvailabilityStatus.FREE) {
                issues.add(issue(context, assignment, status == AvailabilityStatus.BUSY ? "BUSY_ASSIGNED" : "NOT_SUBMITTED"));
            }
        }
        return issues;
    }

    private List<ScheduleIssueResponse> inactiveEmployment(ScheduleContext context) {
        return context.assignments().stream()
                .filter(assignment -> context.person(assignment.getStaffId()) == null
                        || context.person(assignment.getStaffId()).typeOn(assignment.getWorkDate()).isEmpty())
                .map(assignment -> issue(context, assignment, "EMPLOYMENT_NOT_ACTIVE"))
                .toList();
    }

    private List<ScheduleIssueResponse> belowMinimumHours(ScheduleContext context) {
        Map<UUID, Integer> minimum = limitsProvider.minMinutes(context.limits(), context.staff(), context.schedule().getWeekStart());
        Map<UUID, Integer> worked = weeklyHours.minutesByStaff(context.assignments());
        return context.staff().values().stream()
                .filter(person -> person.typeOn(context.schedule().getWeekStart()).isPresent())
                .filter(person -> worked.getOrDefault(person.staffId(), 0) < minimum.get(person.staffId()))
                .map(person -> new ScheduleIssueResponse("BELOW_MIN_HOURS", null, person.staffId(), person.displayName(), null,
                        worked.getOrDefault(person.staffId(), 0) / 60.0 + "/" + minimum.get(person.staffId()) / 60.0))
                .toList();
    }

    private List<ScheduleIssueResponse> shortRest(ScheduleContext context) {
        List<ScheduleIssueResponse> issues = new ArrayList<>();
        Map<UUID, List<ShiftAssignment>> byStaff = context.assignments().stream().collect(Collectors.groupingBy(ShiftAssignment::getStaffId));
        byStaff.values().forEach(shifts -> {
            List<ShiftAssignment> ordered = shifts.stream()
                    .sorted(Comparator.comparing(ShiftAssignment::getWorkDate).thenComparing(ShiftAssignment::getStartTime)).toList();
            for (int index = 1; index < ordered.size(); index++) {
                ShiftAssignment previous = ordered.get(index - 1);
                ShiftAssignment next = ordered.get(index);
                long restHours = Duration.between(LocalDateTime.of(previous.getWorkDate(), previous.getEndTime()),
                        LocalDateTime.of(next.getWorkDate(), next.getStartTime())).toHours();
                if (!previous.getWorkDate().equals(next.getWorkDate()) && restHours < context.limits().minRestHours()) {
                    issues.add(issue(context, next, "REST_TOO_SHORT"));
                }
            }
        });
        return issues;
    }

    private ScheduleIssueResponse issue(ScheduleContext context, ShiftAssignment assignment, String type) {
        SchedulingStaff person = context.person(assignment.getStaffId());
        return new ScheduleIssueResponse(type, assignment.getWorkDate(), assignment.getStaffId(),
                person == null ? null : person.displayName(), assignment.getPositionId(),
                assignment.getStartTime() + "-" + assignment.getEndTime());
    }
}

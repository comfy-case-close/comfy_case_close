package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.exception.PayrollExceptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Keeps a published schedule at its minimum staffing when a store manager removes someone. */
@Component
@RequiredArgsConstructor
public class StaffingGuard {

    private final CoverageCalculator coverageCalculator;

    public void requireStillCovered(ScheduleContext context, ShiftAssignment removed) {
        Set<String> before = missing(context, context.assignments());
        Set<String> after = missing(context, context.assignments().stream()
                .filter(assignment -> !assignment.getShiftAssignmentId().equals(removed.getShiftAssignmentId())).toList());
        after.removeAll(before);
        if (!after.isEmpty()) {
            throw PayrollExceptions.minimumStaffingViolation("Removing this person leaves a time window below the minimum staffing");
        }
    }

    private Set<String> missing(ScheduleContext context, List<ShiftAssignment> assignments) {
        Set<String> missing = new HashSet<>();
        coverageCalculator.compute(context.schedule().getBranchId(), context.schedule().getWeekStart(), context.slots(), assignments)
                .forEach(segment -> segment.missingPositions().forEach(positionId ->
                        missing.add(segment.date() + "|" + segment.period() + "|" + segment.window() + "|" + positionId)));
        return missing;
    }
}

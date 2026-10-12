package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.identity.entity.Branch;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentGuard {

    private final ShiftAssignmentRepository assignmentRepository;
    private final EntityManager entityManager;

    public AvailabilityStatus requireAssignable(ScheduleContext context, UUID staffId, LocalDate date, ShiftSlot slot,
            UUID positionId, TimeWindow window, UUID ignoredAssignmentId, String reason) {
        return requireAssignableIgnoring(context, staffId, date, slot, positionId, window,
                ignoredAssignmentId == null ? Set.of() : Set.of(ignoredAssignmentId), reason);
    }

    public AvailabilityStatus requireAssignableIgnoring(ScheduleContext context, UUID staffId, LocalDate date, ShiftSlot slot,
            UUID positionId, TimeWindow window, Set<UUID> ignoredAssignmentIds, String reason) {
        SchedulingStaff person = context.person(staffId);
        requireEligible(person, date, slot, positionId);
        requireNoConflict(context, staffId, date, window, ignoredAssignmentIds);
        AvailabilityStatus status = context.availability().statusOf(staffId, date, window);
        requireNotOnLeave(status, date);
        boolean reasonGiven = reason != null && !reason.isBlank();
        if (status != AvailabilityStatus.FREE && !reasonGiven) {
            throw PayrollExceptions.invalidField("A reason is required to schedule an employee who is busy or has not registered");
        }
        if (!person.belongsTo(context.schedule().getBranchId()) && !reasonGiven) {
            throw PayrollExceptions.invalidField("A reason is required to schedule an employee outside their default branch");
        }
        return status;
    }

    private void requireEligible(SchedulingStaff person, LocalDate date, ShiftSlot slot, UUID positionId) {
        boolean eligible = person != null && person.canFill(positionId)
                && person.typeOn(date).filter(type -> type == slot.getEmploymentType()).isPresent();
        if (!eligible) {
            throw PayrollExceptions.staffNotEligible("The employee does not work at this branch, cannot fill this position or has no matching contract");
        }
    }

    private void requireNotOnLeave(AvailabilityStatus status, LocalDate date) {
        if (status == AvailabilityStatus.ON_LEAVE) {
            throw PayrollExceptions.staffOnLeave("The employee has leave or an absence recorded on " + date);
        }
    }

    private void requireNoConflict(ScheduleContext context, UUID staffId, LocalDate date, TimeWindow window,
            Set<UUID> ignoredAssignmentIds) {
        for (ShiftAssignment other : assignmentRepository.findByStaffIdAndWorkDateBetween(staffId, date, date)) {
            if (ignoredAssignmentIds.contains(other.getShiftAssignmentId())) {
                continue;
            }
            if (!other.getBranchId().equals(context.schedule().getBranchId())) {
                throw PayrollExceptions.branchConflictSameDay("The employee already works at "
                        + entityManager.find(Branch.class, other.getBranchId()).getBranchName() + " on " + date);
            }
            if (TimeWindow.of(other).overlaps(window)) {
                throw PayrollExceptions.shiftOverlap("The employee already has an overlapping shift on " + date);
            }
        }
    }
}

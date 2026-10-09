package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.identity.entity.Branch;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The rules every added or replacing person must pass; the database repeats the day and overlap rules as the last guard. */
@Component
@RequiredArgsConstructor
public class AssignmentGuard {

    private final ShiftAssignmentRepository assignmentRepository;
    private final EntityManager entityManager;

    /** @return how the person stands against their own registration; anything but FREE needs a reason from the caller */
    public AvailabilityStatus requireAssignable(ScheduleContext context, UUID staffId, LocalDate date, ShiftSlot slot,
            UUID positionId, TimeWindow window, UUID ignoredAssignmentId, String reason) {
        SchedulingStaff person = context.person(staffId);
        if (person == null || !person.canFill(positionId) || person.typeOn(date).filter(type -> type == slot.getEmploymentType()).isEmpty()) {
            throw PayrollExceptions.staffNotEligible("The employee does not work at this branch, cannot fill this position or has no matching contract");
        }
        requireNoConflict(context, staffId, date, window, ignoredAssignmentId);
        AvailabilityStatus status = context.availability().statusOf(staffId, date, window);
        if (status != AvailabilityStatus.FREE && (reason == null || reason.isBlank())) {
            throw PayrollExceptions.invalidField("A reason is required to schedule an employee who is busy or has not registered");
        }
        return status;
    }

    private void requireNoConflict(ScheduleContext context, UUID staffId, LocalDate date, TimeWindow window, UUID ignoredAssignmentId) {
        for (ShiftAssignment other : assignmentRepository.findByStaffIdAndWorkDateBetween(staffId, date, date)) {
            if (other.getShiftAssignmentId().equals(ignoredAssignmentId)) {
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

package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.AssignmentEventType;
import com.fnbx.hrm.enums.AssignmentSource;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentStore {

    private final ShiftAssignmentRepository assignmentRepository;
    private final AssignmentEventRecorder events;

    public record NewAssignment(UUID staffId, LocalDate date, ShiftSlot slot, UUID positionId, TimeWindow window,
                                AssignmentSource source, UUID generationRunId, String note) {}

    public ShiftAssignment add(ShiftSchedule schedule, NewAssignment request, String reason) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setShiftAssignmentId(UUID.randomUUID());
        assignment.setBusinessId(schedule.getBusinessId());
        assignment.setShiftScheduleId(schedule.getShiftScheduleId());
        assignment.setBranchId(schedule.getBranchId());
        assignment.setStaffId(request.staffId());
        assignment.setWorkDate(request.date());
        assignment.setShiftSlotId(request.slot().getShiftSlotId());
        assignment.setPositionId(request.positionId());
        assignment.setStartTime(request.window().start());
        assignment.setEndTime(request.window().end());
        assignment.setSource(request.source());
        assignment.setGenerationRunId(request.generationRunId());
        assignment.setNote(request.note());
        save(assignment);
        events.record(assignment, AssignmentEventType.ADDED, null, events.snapshot(assignment), reason);
        return assignment;
    }

    public ShiftAssignment replace(ShiftAssignment assignment, UUID newStaffId, String reason) {
        var before = events.snapshot(assignment);
        assignment.setStaffId(newStaffId);
        assignment.setSource(AssignmentSource.MANUAL);
        assignment.setGenerationRunId(null);
        assignment.setVersion(assignment.getVersion() + 1);
        save(assignment);
        events.record(assignment, AssignmentEventType.REPLACED, before, events.snapshot(assignment), reason);
        return assignment;
    }

    private record ShiftPlace(LocalDate date, UUID slotId, UUID positionId, LocalTime start, LocalTime end) {

        static ShiftPlace of(ShiftAssignment assignment) {
            return new ShiftPlace(assignment.getWorkDate(), assignment.getShiftSlotId(), assignment.getPositionId(),
                    assignment.getStartTime(), assignment.getEndTime());
        }
    }

    public void exchange(ShiftAssignment first, ShiftAssignment second, String reason) {
        var firstBefore = events.snapshot(first);
        var secondBefore = events.snapshot(second);
        ShiftPlace firstPlace = ShiftPlace.of(first);
        ShiftPlace secondPlace = ShiftPlace.of(second);
        // swap the shifts, not the people: nobody holds both at once, so the no-overlap constraint stays satisfied
        moveTo(first, secondPlace);
        moveTo(second, firstPlace);
        events.record(first, AssignmentEventType.TIME_CHANGED, firstBefore, events.snapshot(first), reason);
        events.record(second, AssignmentEventType.TIME_CHANGED, secondBefore, events.snapshot(second), reason);
    }

    private void moveTo(ShiftAssignment assignment, ShiftPlace place) {
        assignment.setWorkDate(place.date());
        assignment.setShiftSlotId(place.slotId());
        assignment.setPositionId(place.positionId());
        assignment.setStartTime(place.start());
        assignment.setEndTime(place.end());
        assignment.setSource(AssignmentSource.MANUAL);
        assignment.setGenerationRunId(null);
        assignment.setVersion(assignment.getVersion() + 1);
        save(assignment);
    }

    public ShiftAssignment changeTime(ShiftAssignment assignment, TimeWindow window, String note) {
        var before = events.snapshot(assignment);
        assignment.setStartTime(window.start());
        assignment.setEndTime(window.end());
        assignment.setNote(note);
        assignment.setVersion(assignment.getVersion() + 1);
        save(assignment);
        events.record(assignment, AssignmentEventType.TIME_CHANGED, before, events.snapshot(assignment), null);
        return assignment;
    }

    public void remove(ShiftAssignment assignment, String reason) {
        events.record(assignment, AssignmentEventType.REMOVED, events.snapshot(assignment), null, reason);
        assignmentRepository.delete(assignment);
        assignmentRepository.flush();
    }

    private void save(ShiftAssignment assignment) {
        try {
            assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(DataIntegrityViolationException ex) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (detail.contains("fk_shift_assignment_staff_day_branch")) {
            return PayrollExceptions.branchConflictSameDay("The employee already works at another branch on that day");
        }
        if (detail.contains("ex_shift_assignment_no_overlap") || detail.contains("uq_shift_assignment_slot")) {
            return PayrollExceptions.shiftOverlap("The employee already has an overlapping shift");
        }
        return ex;
    }
}

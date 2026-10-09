package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.AssignmentSource;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.service.scheduling.AssignmentStore.NewAssignment;
import com.fnbx.shared.exception.AppException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Starts a week from an earlier one; anyone who cannot work the same shift again is left out. */
@Component
@RequiredArgsConstructor
public class ScheduleCopier {

    private static final String COPY_REASON = "Copied from the previous schedule";

    private final ShiftScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftSlotRepository slotRepository;
    private final ScheduleContextLoader contextLoader;
    private final AssignmentGuard guard;
    private final AssignmentStore store;

    public int copy(ShiftSchedule target, LocalDate fromWeekStart) {
        Optional<ShiftSchedule> source = scheduleRepository.findByBranchIdAndWeekStart(target.getBranchId(), fromWeekStart);
        if (source.isEmpty()) {
            return 0;
        }
        ScheduleContext context = contextLoader.load(target);
        long shift = ChronoUnit.DAYS.between(fromWeekStart, target.getWeekStart());
        List<ShiftAssignment> earlier = assignmentRepository.findByShiftScheduleId(source.get().getShiftScheduleId());
        Map<java.util.UUID, ShiftSlot> earlierSlots = slotRepository.findAllById(earlier.stream().map(ShiftAssignment::getShiftSlotId).toList())
                .stream().collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
        int copied = 0;
        for (ShiftAssignment assignment : earlier) {
            LocalDate date = assignment.getWorkDate().plusDays(shift);
            Optional<ShiftSlot> slot = sameSlot(context, date, earlierSlots.get(assignment.getShiftSlotId()));
            if (slot.isPresent() && copyOne(context, assignment, date, slot.get())) {
                copied++;
            }
        }
        return copied;
    }

    private boolean copyOne(ScheduleContext context, ShiftAssignment assignment, LocalDate date, ShiftSlot slot) {
        TimeWindow window = TimeWindow.of(slot);
        try {
            guard.requireAssignable(context, assignment.getStaffId(), date, slot, assignment.getPositionId(), window, null, COPY_REASON);
        } catch (AppException ex) {
            return false;
        }
        store.add(context.schedule(), new NewAssignment(assignment.getStaffId(), date, slot, assignment.getPositionId(), window,
                AssignmentSource.MANUAL, null, null), COPY_REASON);
        return true;
    }

    private Optional<ShiftSlot> sameSlot(ScheduleContext context, LocalDate date, ShiftSlot original) {
        return context.slots().on(date).stream()
                .filter(slot -> slot.getName().equals(original.getName()) && slot.getEmploymentType() == original.getEmploymentType())
                .findFirst();
    }
}

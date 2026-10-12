package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleContextLoader {

    private final ShiftAssignmentRepository assignmentRepository;
    private final SlotCatalog slotCatalog;
    private final SchedulingStaffLoader staffLoader;
    private final AvailabilityLoader availabilityLoader;
    private final SchedulingLimitsProvider limitsProvider;

    public ScheduleContext load(ShiftSchedule schedule) {
        List<ShiftAssignment> assignments = assignmentRepository.findByShiftScheduleId(schedule.getShiftScheduleId());
        WeekSlots slots = slotCatalog.forWeek(schedule.getBranchId(), schedule.getWeekStart(),
                assignments.stream().map(ShiftAssignment::getShiftSlotId).toList());
        Map<UUID, SchedulingStaff> staff = new HashMap<>(staffLoader.forBranch(schedule.getBranchId(), schedule.getWeekStart()));
        Set<UUID> missing = new HashSet<>(assignments.stream().map(ShiftAssignment::getStaffId).toList());
        missing.removeAll(staff.keySet());
        staff.putAll(staffLoader.byIds(missing, schedule.getWeekStart()));
        AvailabilityIndex availability = availabilityLoader.load(schedule.getWeekStart(), staff.keySet());
        return new ScheduleContext(schedule, slots, staff, availability, limitsProvider.forBranch(schedule.getBranchId()), assignments);
    }
}

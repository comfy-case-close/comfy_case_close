package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One branch week with everything needed to judge it: slots, the people involved, their availability and the assignments. */
public record ScheduleContext(
        ShiftSchedule schedule,
        WeekSlots slots,
        Map<UUID, SchedulingStaff> staff,
        AvailabilityIndex availability,
        SchedulingLimits limits,
        List<ShiftAssignment> assignments) {

    public SchedulingStaff person(UUID staffId) {
        return staff.get(staffId);
    }
}

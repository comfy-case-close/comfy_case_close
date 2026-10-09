package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WeeklyHours {

    public Map<UUID, Integer> minutesByStaff(List<ShiftAssignment> assignments) {
        Map<UUID, Integer> minutes = new HashMap<>();
        assignments.forEach(assignment -> minutes.merge(assignment.getStaffId(), TimeWindow.of(assignment).minutes(), Integer::sum));
        return minutes;
    }
}

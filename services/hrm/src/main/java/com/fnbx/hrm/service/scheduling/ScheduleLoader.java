package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleLoader {

    private final ShiftScheduleRepository scheduleRepository;

    public ShiftSchedule require(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).orElseThrow(PayrollExceptions::scheduleNotFound);
    }

    public void requireVersion(ShiftSchedule schedule, long expectedVersion) {
        if (schedule.getVersion() != expectedVersion) {
            throw PayrollExceptions.versionConflict();
        }
    }
}

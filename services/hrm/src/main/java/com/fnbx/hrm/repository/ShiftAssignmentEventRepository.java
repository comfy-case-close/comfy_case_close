package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ShiftAssignmentEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAssignmentEventRepository extends JpaRepository<ShiftAssignmentEvent, UUID> {

    List<ShiftAssignmentEvent> findByShiftScheduleIdOrderByOccurredAt(UUID shiftScheduleId);

    List<ShiftAssignmentEvent> findByShiftScheduleIdAndOccurredAtAfterOrderByOccurredAt(UUID shiftScheduleId, Instant after);
}

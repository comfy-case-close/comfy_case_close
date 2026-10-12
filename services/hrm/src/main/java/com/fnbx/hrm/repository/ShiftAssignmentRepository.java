package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ShiftAssignment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

    List<ShiftAssignment> findByShiftScheduleId(UUID shiftScheduleId);

    List<ShiftAssignment> findByShiftScheduleIdIn(Collection<UUID> shiftScheduleIds);

    List<ShiftAssignment> findByStaffIdInAndWorkDateBetween(Collection<UUID> staffIds, LocalDate from, LocalDate to);

    List<ShiftAssignment> findByStaffIdAndWorkDateBetween(UUID staffId, LocalDate from, LocalDate to);

    List<ShiftAssignment> findByGenerationRunId(UUID generationRunId);
}

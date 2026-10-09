package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.AttendanceSheet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSheetRepository extends JpaRepository<AttendanceSheet, UUID> {

    Optional<AttendanceSheet> findByShiftScheduleId(UUID shiftScheduleId);

    List<AttendanceSheet> findByShiftScheduleIdIn(Collection<UUID> shiftScheduleIds);
}

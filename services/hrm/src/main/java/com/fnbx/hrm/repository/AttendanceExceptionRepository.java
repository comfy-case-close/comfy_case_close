package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.AttendanceException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceExceptionRepository extends JpaRepository<AttendanceException, UUID> {

    List<AttendanceException> findByShiftAssignmentIdIn(Collection<UUID> shiftAssignmentIds);
}

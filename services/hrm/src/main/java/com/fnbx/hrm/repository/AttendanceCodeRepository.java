package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.AttendanceCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCodeRepository extends JpaRepository<AttendanceCode, UUID> {

    List<AttendanceCode> findAllByOrderByCode();

    Optional<AttendanceCode> findByCode(String code);
}

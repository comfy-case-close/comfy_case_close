package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.EmployeeLeaveQuota;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeLeaveQuotaRepository extends JpaRepository<EmployeeLeaveQuota, UUID> {

    Optional<EmployeeLeaveQuota> findByStaffIdAndLeaveYear(UUID staffId, short leaveYear);
}

package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.RegistrationWindow;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationWindowRepository extends JpaRepository<RegistrationWindow, UUID> {

    Optional<RegistrationWindow> findByBranchIdAndWeekStart(UUID branchId, LocalDate weekStart);
}

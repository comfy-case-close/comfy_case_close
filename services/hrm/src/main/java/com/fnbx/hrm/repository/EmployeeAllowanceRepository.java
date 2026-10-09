package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.EmployeeAllowance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeAllowanceRepository extends JpaRepository<EmployeeAllowance, UUID> {

    List<EmployeeAllowance> findByStaffIdOrderByEffectiveFromDesc(UUID staffId);

    @Query("""
           SELECT a FROM EmployeeAllowance a
           WHERE a.staffId = :staffId
             AND a.effectiveFrom <= :asOf
             AND (a.effectiveTo IS NULL OR a.effectiveTo >= :asOf)
           """)
    Optional<EmployeeAllowance> findEffective(@Param("staffId") UUID staffId, @Param("asOf") LocalDate asOf);
}

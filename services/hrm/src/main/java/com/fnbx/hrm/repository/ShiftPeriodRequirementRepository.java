package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ShiftPeriodRequirement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftPeriodRequirementRepository extends JpaRepository<ShiftPeriodRequirement, UUID> {

    @Query("""
           SELECT r FROM ShiftPeriodRequirement r
           WHERE r.branchId = :branchId AND r.effectiveFrom <= :asOf
           ORDER BY r.effectiveFrom DESC
           """)
    List<ShiftPeriodRequirement> findInForce(@Param("branchId") UUID branchId, @Param("asOf") LocalDate asOf);

    @Modifying
    @Query("DELETE FROM ShiftPeriodRequirement r WHERE r.branchId = :branchId AND r.effectiveFrom = :effectiveFrom")
    void deleteByBranchIdAndEffectiveFrom(@Param("branchId") UUID branchId, @Param("effectiveFrom") LocalDate effectiveFrom);
}

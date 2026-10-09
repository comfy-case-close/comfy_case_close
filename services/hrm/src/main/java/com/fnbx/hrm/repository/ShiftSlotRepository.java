package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.ShiftDayType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftSlotRepository extends JpaRepository<ShiftSlot, UUID> {

    @Query("""
           SELECT s FROM ShiftSlot s
           WHERE s.branchId = :branchId AND s.active = TRUE AND s.dayType = :dayType
             AND s.effectiveFrom <= :date AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
           ORDER BY s.sortOrder, s.startTime
           """)
    List<ShiftSlot> findEffective(@Param("branchId") UUID branchId, @Param("date") LocalDate date,
            @Param("dayType") ShiftDayType dayType);

    @Query("""
           SELECT s FROM ShiftSlot s
           WHERE s.branchId = :branchId
             AND s.effectiveFrom <= :asOf AND (s.effectiveTo IS NULL OR s.effectiveTo >= :asOf)
           ORDER BY s.dayType, s.sortOrder, s.startTime
           """)
    List<ShiftSlot> findAllEffective(@Param("branchId") UUID branchId, @Param("asOf") LocalDate asOf);

    @Query("""
           SELECT s FROM ShiftSlot s
           WHERE s.branchId = :branchId AND s.name = :name AND s.dayType = :dayType
             AND s.effectiveFrom < :newFrom AND (s.effectiveTo IS NULL OR s.effectiveTo >= :newFrom)
           """)
    List<ShiftSlot> findCurrentVersions(@Param("branchId") UUID branchId, @Param("name") String name,
            @Param("dayType") ShiftDayType dayType, @Param("newFrom") LocalDate newFrom);
}

package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.enums.ScheduleStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, UUID> {

    Optional<ShiftSchedule> findByBranchIdAndWeekStart(UUID branchId, LocalDate weekStart);

    List<ShiftSchedule> findByStatusOrderByWeekStart(ScheduleStatus status);

    @Query("""
           SELECT s FROM ShiftSchedule s
           WHERE s.weekStart <= :to AND s.weekStart >= :fromWeekStart
           ORDER BY s.weekStart, s.branchId
           """)
    List<ShiftSchedule> findWeeksBetween(@Param("fromWeekStart") LocalDate fromWeekStart, @Param("to") LocalDate to);

    @Query("""
           SELECT s FROM ShiftSchedule s
           WHERE s.branchId IN :branchIds
             AND (cast(:weekStart as date) IS NULL OR s.weekStart = :weekStart)
             AND (:status IS NULL OR cast(s.status as string) = :status)
           ORDER BY s.weekStart DESC, s.branchId
           """)
    List<ShiftSchedule> search(@Param("branchIds") Collection<UUID> branchIds, @Param("weekStart") LocalDate weekStart,
                               @Param("status") String status);
}

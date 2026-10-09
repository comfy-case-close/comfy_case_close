package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.TimesheetEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {

    List<TimesheetEntry> findByPayrollLineIdIn(List<UUID> payrollLineIds);

    Optional<TimesheetEntry> findByPayrollLineIdAndWorkDate(UUID payrollLineId, LocalDate workDate);

    void deleteByPayrollLineIdAndWorkDate(UUID payrollLineId, LocalDate workDate);

    @Query("""
           SELECT count(DISTINCT t.workDate) FROM TimesheetEntry t, PayrollLine l, EmploymentAssignment a, AttendanceCode c
           WHERE t.payrollLineId = l.payrollLineId
             AND l.assignmentId = a.employmentAssignmentId
             AND t.attendanceCodeId = c.attendanceCodeId
             AND a.staffId = :staffId
             AND c.code = :code
             AND t.workDate BETWEEN :yearStart AND :yearEnd
           """)
    long countDistinctDatesByEmployeeAndCode(@Param("staffId") UUID staffId, @Param("code") String code,
            @Param("yearStart") LocalDate yearStart, @Param("yearEnd") LocalDate yearEnd);

    @Modifying
    @Query("DELETE FROM TimesheetEntry t WHERE t.payrollLineId = :lineId")
    void deleteAllByLine(@Param("lineId") UUID lineId);
}

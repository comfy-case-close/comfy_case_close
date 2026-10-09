package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.EmploymentAssignment;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmploymentAssignmentRepository extends JpaRepository<EmploymentAssignment, UUID> {

    List<EmploymentAssignment> findByStaffIdOrderByEffectiveFromDesc(UUID staffId);

    @Query(value = "SELECT nextval('payroll.contract_no_seq')", nativeQuery = true)
    long nextContractSequence();

    @Query("""
           SELECT a FROM EmploymentAssignment a
           WHERE a.staffId IN :staffIds
             AND a.effectiveFrom <= :asOf AND (a.effectiveTo IS NULL OR a.effectiveTo >= :asOf)
           """)
    List<EmploymentAssignment> findEffectiveForStaff(@Param("staffIds") Collection<UUID> staffIds, @Param("asOf") LocalDate asOf);

    @Query("""
           SELECT a FROM EmploymentAssignment a
           WHERE a.effectiveFrom <= :to AND (a.effectiveTo IS NULL OR a.effectiveTo >= :from)
           """)
    List<EmploymentAssignment> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT a FROM EmploymentAssignment a
           WHERE a.effectiveTo IS NOT NULL AND a.effectiveTo BETWEEN :from AND :to
           """)
    List<EmploymentAssignment> findEndingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}

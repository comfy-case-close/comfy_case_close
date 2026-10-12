package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.enums.EmploymentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID> {

    List<PayrollLine> findByPeriodId(UUID periodId);

    List<PayrollLine> findByPeriodIdIn(java.util.Collection<UUID> periodIds);

    @Query("""
           SELECT l FROM PayrollLine l
           WHERE l.periodId = :periodId
             AND (:anyEmploymentType = TRUE OR l.employmentType = :employmentType)
             AND (:anyBranch = TRUE OR l.branchId = :branchId)
           """)
    List<PayrollLine> findByPeriodIdAndFiltersFiltered(@Param("periodId") UUID periodId,
            @Param("anyEmploymentType") boolean anyEmploymentType,
            @Param("employmentType") EmploymentType employmentType, @Param("anyBranch") boolean anyBranch,
            @Param("branchId") UUID branchId);

    default List<PayrollLine> findByPeriodIdAndFilters(UUID periodId, EmploymentType employmentType, UUID branchId) {
        return findByPeriodIdAndFiltersFiltered(periodId, employmentType == null, employmentType, branchId == null, branchId);
    }

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayrollLineResponse(
               l.payrollLineId, l.periodId, l.assignmentId, s.staffId, s.employeeCode,
               concat(s.firstName, ' ', s.lastName), a.positionId, l.branchId, cast(l.employmentType as string),
               l.totalHours, l.standardHours, l.overtimeHours, l.weekendHours, l.regularHours,
               l.standardWorkdays, l.allowanceHours, l.grossPay, l.employeeInsuranceTotal,
               l.employerInsuranceTotal, l.deductionTotal, l.netPay, l.laborCost,
               l.lateDayCount, l.lateShiftCount, l.absenceDayCount, l.hasInvalidCode, l.calculatedAt, l.note, l.version)
           FROM PayrollLine l, EmploymentAssignment a, Staff s
           WHERE l.assignmentId = a.employmentAssignmentId AND a.staffId = s.staffId
             AND l.periodId = :periodId
             AND (:anyEmploymentType = TRUE OR l.employmentType = :employmentType)
             AND (:anyBranch = TRUE OR l.branchId = :branchId)
           ORDER BY s.employeeCode
           """)
    List<com.fnbx.hrm.dto.response.PayrollLineResponse> findLineResponsesFiltered(@Param("periodId") UUID periodId,
            @Param("anyEmploymentType") boolean anyEmploymentType,
            @Param("employmentType") EmploymentType employmentType, @Param("anyBranch") boolean anyBranch,
            @Param("branchId") UUID branchId);

    default List<com.fnbx.hrm.dto.response.PayrollLineResponse> findLineResponses(UUID periodId, EmploymentType employmentType, UUID branchId) {
        return findLineResponsesFiltered(periodId, employmentType == null, employmentType, branchId == null, branchId);
    }

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayrollLineSummaryResponse(
               l.payrollLineId, l.periodId, l.assignmentId, s.staffId, s.employeeCode,
               concat(s.firstName, ' ', s.lastName), a.positionId, l.branchId,
               cast(l.employmentType as string), l.note, l.version)
           FROM PayrollLine l, EmploymentAssignment a, Staff s
           WHERE l.assignmentId = a.employmentAssignmentId AND a.staffId = s.staffId
             AND l.periodId = :periodId
             AND (:anyEmploymentType = TRUE OR l.employmentType = :employmentType)
             AND (:anyBranch = TRUE OR l.branchId = :branchId)
           ORDER BY s.employeeCode
           """)
    List<com.fnbx.hrm.dto.response.PayrollLineSummaryResponse> findLineSummariesFiltered(@Param("periodId") UUID periodId,
            @Param("anyEmploymentType") boolean anyEmploymentType,
            @Param("employmentType") EmploymentType employmentType, @Param("anyBranch") boolean anyBranch,
            @Param("branchId") UUID branchId);

    default List<com.fnbx.hrm.dto.response.PayrollLineSummaryResponse> findLineSummaries(UUID periodId, EmploymentType employmentType, UUID branchId) {
        return findLineSummariesFiltered(periodId, employmentType == null, employmentType, branchId == null, branchId);
    }

    @Query("""
           SELECT l FROM PayrollLine l, EmploymentAssignment a
           WHERE l.assignmentId = a.employmentAssignmentId AND l.periodId = :periodId
             AND a.staffId = :staffId AND l.branchId = :branchId
           """)
    List<PayrollLine> findByPeriodAndStaffAndBranch(@Param("periodId") UUID periodId, @Param("staffId") UUID staffId,
            @Param("branchId") UUID branchId);

    Optional<PayrollLine> findByPeriodIdAndAssignmentIdAndBranchId(UUID periodId, UUID assignmentId, UUID branchId);

    boolean existsByPeriodIdAndAssignmentIdAndBranchId(UUID periodId, UUID assignmentId, UUID branchId);

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayrollHistoryEntryResponse(
               p.payrollPeriodId, p.periodYear, p.periodMonth, l.payrollLineId, l.grossPay, l.netPay)
           FROM PayrollLine l, EmploymentAssignment a, PayrollPeriod p
           WHERE l.assignmentId = a.employmentAssignmentId
             AND l.periodId = p.payrollPeriodId
             AND a.staffId = :staffId
           ORDER BY p.periodYear DESC, p.periodMonth DESC
           """)
    List<com.fnbx.hrm.dto.response.PayrollHistoryEntryResponse> findPayrollHistory(@Param("staffId") UUID staffId);

    @Query("SELECT count(t) > 0 FROM TimesheetEntry t WHERE t.payrollLineId = :lineId")
    boolean hasTimesheetEntries(@Param("lineId") UUID lineId);

    @Query("""
           SELECT new com.fnbx.hrm.repository.BranchCostTotals(
               l.branchId, cast(l.employmentType as string), count(l), sum(l.grossPay), sum(l.employerInsuranceTotal),
               sum(l.laborCost))
           FROM PayrollLine l
           WHERE l.periodId IN :periodIds
             AND NOT EXISTS (SELECT 1 FROM SharedCostAllocation a WHERE a.payrollLineId = l.payrollLineId)
           GROUP BY l.branchId, l.employmentType
           """)
    List<BranchCostTotals> findDirectBranchCost(@Param("periodIds") java.util.Collection<UUID> periodIds);

    @Query("""
           SELECT new com.fnbx.hrm.repository.BranchCostTotals(
               a.targetBranchId, cast(l.employmentType as string), 0L, sum(a.allocatedGross),
               sum(a.allocatedEmployerInsurance), sum(a.allocatedCost))
           FROM SharedCostAllocation a, PayrollLine l
           WHERE a.payrollLineId = l.payrollLineId AND l.periodId IN :periodIds
           GROUP BY a.targetBranchId, l.employmentType
           """)
    List<BranchCostTotals> findAllocatedBranchCost(@Param("periodIds") java.util.Collection<UUID> periodIds);

    @Query("""
           SELECT count(distinct a.staffId)
           FROM PayrollLine l, EmploymentAssignment a
           WHERE l.assignmentId = a.employmentAssignmentId AND l.periodId IN :periodIds AND l.grossPay > 0
           """)
    long countPaidStaff(@Param("periodIds") java.util.Collection<UUID> periodIds);

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.EmployeePeriodSummaryResponse(
               s.staffId, s.employeeCode, concat(s.firstName, ' ', s.lastName),
               sum(l.grossPay), count(l), sum(l.lateDayCount))
           FROM PayrollLine l, EmploymentAssignment a, Staff s
           WHERE l.assignmentId = a.employmentAssignmentId AND a.staffId = s.staffId AND l.periodId = :periodId
           GROUP BY s.staffId, s.employeeCode, s.firstName, s.lastName
           ORDER BY s.employeeCode
           """)
    List<com.fnbx.hrm.dto.response.EmployeePeriodSummaryResponse> findEmployeeSummaries(@Param("periodId") UUID periodId);

    @Query("""
           SELECT count(l) > 0 FROM PayrollLine l, PayrollPeriod p
           WHERE l.periodId = p.payrollPeriodId AND l.assignmentId = :assignmentId
             AND cast(p.status as string) <> 'DRAFT'
           """)
    boolean usedByNonDraftPeriod(@Param("assignmentId") UUID assignmentId);
}

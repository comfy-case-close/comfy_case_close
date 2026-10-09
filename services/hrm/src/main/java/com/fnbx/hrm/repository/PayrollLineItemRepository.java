package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollLineItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollLineItemRepository extends JpaRepository<PayrollLineItem, UUID> {

    List<PayrollLineItem> findByPayrollLineId(UUID payrollLineId);

    List<PayrollLineItem> findByPayrollLineIdIn(List<UUID> payrollLineIds);

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayrollLineItemResponse(
               c.componentCode, cast(c.componentType as string), i.quantity, i.rate, i.amount, i.calcNote)
           FROM PayrollLineItem i, PayComponent c
           WHERE i.componentId = c.payComponentId AND i.payrollLineId = :lineId
           ORDER BY c.displayOrder
           """)
    List<com.fnbx.hrm.dto.response.PayrollLineItemResponse> findItemResponses(@Param("lineId") UUID lineId);

    @Query("""
           SELECT i FROM PayrollLineItem i, PayComponent c
           WHERE i.componentId = c.payComponentId AND c.manualInput = true AND i.payrollLineId = :lineId
           """)
    List<PayrollLineItem> findManualItems(@Param("lineId") UUID lineId);

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayslipComponentTotalResponse(
               c.componentCode, c.componentName, cast(c.componentType as string), sum(i.amount))
           FROM PayrollLineItem i, PayComponent c, PayrollLine l
           WHERE i.componentId = c.payComponentId AND i.payrollLineId = l.payrollLineId
             AND l.periodId = :periodId AND l.assignmentId IN (
                 SELECT a.employmentAssignmentId FROM EmploymentAssignment a WHERE a.staffId = :staffId)
           GROUP BY c.componentCode, c.componentName, c.componentType, c.displayOrder
           ORDER BY c.displayOrder
           """)
    List<com.fnbx.hrm.dto.response.PayslipComponentTotalResponse> sumComponentsForEmployee(
            @Param("periodId") UUID periodId, @Param("staffId") UUID staffId);

    @Query("""
           SELECT new com.fnbx.hrm.repository.BranchComponentTotals(
               l.branchId, cast(l.employmentType as string), c.componentCode, sum(i.amount))
           FROM PayrollLineItem i, PayComponent c, PayrollLine l
           WHERE i.componentId = c.payComponentId AND i.payrollLineId = l.payrollLineId
             AND l.periodId IN :periodIds
           GROUP BY l.branchId, l.employmentType, c.componentCode
           """)
    List<BranchComponentTotals> sumComponentsByBranch(@Param("periodIds") java.util.Collection<UUID> periodIds);

    @Modifying
    @Query("""
           DELETE FROM PayrollLineItem i
           WHERE i.payrollLineId IN :lineIds
             AND i.componentId IN (SELECT c.payComponentId FROM PayComponent c WHERE c.manualInput = false)
           """)
    void deleteEngineComputedByLineIds(@Param("lineIds") List<UUID> lineIds);

    @Modifying
    @Query("DELETE FROM PayrollLineItem i WHERE i.payrollLineId = :lineId")
    void deleteAllByLine(@Param("lineId") UUID lineId);

    @Query("""
           SELECT count(i) > 0 FROM PayrollLineItem i, PayComponent c
           WHERE i.payrollLineId = :lineId AND i.componentId = c.payComponentId AND c.manualInput = TRUE
           """)
    boolean hasManualItems(@Param("lineId") UUID lineId);
}

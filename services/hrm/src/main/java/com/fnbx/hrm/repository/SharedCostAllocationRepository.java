package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.SharedCostAllocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharedCostAllocationRepository extends JpaRepository<SharedCostAllocation, UUID> {

    List<SharedCostAllocation> findByPayrollLineIdIn(List<UUID> payrollLineIds);

    @Query("SELECT a FROM SharedCostAllocation a, PayrollLine l WHERE a.payrollLineId = l.payrollLineId AND l.periodId = :periodId")
    List<SharedCostAllocation> findByPeriodId(@Param("periodId") UUID periodId);

    @Modifying
    @Query("DELETE FROM SharedCostAllocation a WHERE a.payrollLineId IN :lineIds")
    void deleteByLineIds(@Param("lineIds") List<UUID> lineIds);
}

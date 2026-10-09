package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollLineInsurance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollLineInsuranceRepository extends JpaRepository<PayrollLineInsurance, UUID> {

    List<PayrollLineInsurance> findByPayrollLineIdIn(List<UUID> payrollLineIds);

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PayrollLineInsuranceResponse(
               s.schemeCode, i.insuranceBase, i.employerRate, i.employeeRate, i.employerAmount, i.employeeAmount)
           FROM PayrollLineInsurance i, InsuranceScheme s
           WHERE i.schemeId = s.insuranceSchemeId AND i.payrollLineId = :lineId
           """)
    List<com.fnbx.hrm.dto.response.PayrollLineInsuranceResponse> findInsuranceResponses(@Param("lineId") UUID lineId);

    @Modifying
    @Query("DELETE FROM PayrollLineInsurance i WHERE i.payrollLineId IN :lineIds")
    void deleteByLineIds(@Param("lineIds") List<UUID> lineIds);
}

package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.Payslip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    List<Payslip> findByPeriodId(UUID periodId);

    long countByPeriodId(UUID periodId);

    List<Payslip> findByStaffId(UUID staffId);

    @Query("""
           SELECT p FROM Payslip p
           WHERE p.periodId = :periodId
             AND NOT EXISTS (SELECT 1 FROM PayslipEmailLog l
                             WHERE l.payslipId = p.payslipId AND cast(l.status as string) = 'SENT')
           """
    )
    List<Payslip> findUnsentByPeriodId(@Param("periodId") UUID periodId);

    Optional<Payslip> findByPeriodIdAndStaffId(UUID periodId, UUID staffId);
}

package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, UUID> {

    Optional<PayrollPayment> findByPayslipId(UUID payslipId);

    @Query("""
           SELECT p FROM PayrollPayment p, Payslip s
           WHERE p.payslipId = s.payslipId AND s.periodId = :periodId
           """)
    List<PayrollPayment> findByPeriodId(@Param("periodId") UUID periodId);

    @Query("""
           SELECT count(p) > 0 FROM PayrollPayment p, Payslip s
           WHERE p.payslipId = s.payslipId AND s.periodId = :periodId AND cast(p.status as string) = 'PAID'
           """)
    boolean existsPaidInPeriod(@Param("periodId") UUID periodId);

    @Query("""
           SELECT count(p) > 0 FROM PayrollPayment p, Payslip s
           WHERE p.payslipId = s.payslipId AND s.periodId = :periodId AND cast(p.status as string) <> 'PAID'
           """)
    boolean existsUnpaidInPeriod(@Param("periodId") UUID periodId);
}

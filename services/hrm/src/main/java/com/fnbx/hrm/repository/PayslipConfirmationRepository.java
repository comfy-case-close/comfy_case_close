package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayslipConfirmation;
import com.fnbx.hrm.enums.ConfirmationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipConfirmationRepository extends JpaRepository<PayslipConfirmation, UUID> {

    Optional<PayslipConfirmation> findByPayslipId(UUID payslipId);

    Optional<PayslipConfirmation> findByTokenHash(String tokenHash);

    List<PayslipConfirmation> findByPayslipIdIn(Collection<UUID> payslipIds);

    @Query("""
           SELECT c FROM PayslipConfirmation c, Payslip p
           WHERE c.payslipId = p.payslipId AND p.periodId = :periodId AND c.status = :status
           """)
    List<PayslipConfirmation> findByPeriodIdAndStatus(@Param("periodId") UUID periodId,
            @Param("status") ConfirmationStatus status);

    @Query("""
           SELECT count(c) FROM PayslipConfirmation c, Payslip p
           WHERE c.payslipId = p.payslipId AND p.periodId = :periodId AND c.status = :status
           """)
    long countByPeriodIdAndStatus(@Param("periodId") UUID periodId, @Param("status") ConfirmationStatus status);
}

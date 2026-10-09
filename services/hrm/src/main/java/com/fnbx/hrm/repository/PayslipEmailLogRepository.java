package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayslipEmailLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipEmailLogRepository extends JpaRepository<PayslipEmailLog, UUID> {

    List<PayslipEmailLog> findByPayslipIdOrderBySentAtDesc(UUID payslipId);
}

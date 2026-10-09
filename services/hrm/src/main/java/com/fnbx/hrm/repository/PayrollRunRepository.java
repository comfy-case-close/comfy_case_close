package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    List<PayrollRun> findByPeriodIdOrderByStartedAtDesc(UUID periodId);

    Optional<PayrollRun> findFirstByPeriodIdOrderByStartedAtDesc(UUID periodId);
}

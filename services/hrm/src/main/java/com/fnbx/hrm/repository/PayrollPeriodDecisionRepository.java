package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollPeriodDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollPeriodDecisionRepository extends JpaRepository<PayrollPeriodDecision, UUID> {

    List<PayrollPeriodDecision> findByPeriodIdOrderByActedAtAsc(UUID periodId);
}

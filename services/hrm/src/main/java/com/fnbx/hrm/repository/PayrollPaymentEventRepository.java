package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollPaymentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollPaymentEventRepository extends JpaRepository<PayrollPaymentEvent, UUID> {

    List<PayrollPaymentEvent> findByPayrollPaymentIdOrderByActedAt(UUID payrollPaymentId);
}

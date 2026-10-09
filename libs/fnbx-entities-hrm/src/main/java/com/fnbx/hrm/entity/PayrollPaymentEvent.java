package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.PaymentAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "payroll_payment_event")
@Getter
@Setter
@NoArgsConstructor
public class PayrollPaymentEvent {

    @Id
    @Column(name = "payroll_payment_event_id")
    private UUID payrollPaymentEventId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payroll_payment_id", nullable = false)
    private UUID payrollPaymentId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, columnDefinition = "payroll.payment_action")
    private PaymentAction action;

    @Column(name = "acted_by", nullable = false)
    private UUID actedBy;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "reason")
    private String reason;
}

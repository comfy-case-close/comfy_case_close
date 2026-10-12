package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "payroll_payment")
@Getter
@Setter
@NoArgsConstructor
public class PayrollPayment {

    @Id
    @Column(name = "payroll_payment_id")
    private UUID payrollPaymentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(name = "amount", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal amount;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "transfer_note", nullable = false)
    private String transferNote;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.payment_status")
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "bank_reference")
    private String bankReference;

    @Column(name = "failure_reason")
    private String failureReason;
}

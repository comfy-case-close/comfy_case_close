package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.EmailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivery attempt for one {@link Payslip}. One row PER ATTEMPT, never a
 * status column overwritten in place (architecture.md 3.6) - so a resend
 * after a failure keeps the earlier failure visible instead of erasing it.
 */
@Entity
@Table(schema = "payroll", name = "payslip_email_log")
@Getter
@Setter
@NoArgsConstructor
public class PayslipEmailLog {

    @Id
    @Column(name = "payslip_email_log_id")
    private UUID payslipEmailLogId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body")
    private String body;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.email_status")
    private EmailStatus status = EmailStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message")
    private String errorMessage;
}

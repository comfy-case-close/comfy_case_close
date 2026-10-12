package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ConfirmationStatus;
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
@Table(schema = "payroll", name = "payslip_confirmation")
@Getter
@Setter
@NoArgsConstructor
public class PayslipConfirmation {

    @Id
    @Column(name = "payslip_confirmation_id")
    private UUID payslipConfirmationId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.confirmation_status")
    private ConfirmationStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "responded_ip")
    private String respondedIp;

    @Column(name = "responded_user_agent")
    private String respondedUserAgent;

    @Column(name = "dispute_note")
    private String disputeNote;

    @Column(name = "reminder_count")
    private short reminderCount;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

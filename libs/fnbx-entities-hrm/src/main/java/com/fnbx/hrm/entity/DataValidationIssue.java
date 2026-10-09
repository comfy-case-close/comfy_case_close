package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.enums.IssueSeverity;
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
 * What used to be the {@code Canh bao du lieu} sheet's 5 warning blocks plus
 * the 6 reconciliation checks - now rows instead of formulas re-derived on
 * every read (spec section 5.10). {@link IssueSeverity#ERROR} blocks a payroll
 * run; {@link IssueSeverity#WARNING} only blocks locking the period and must
 * be acknowledged first.
 */
@Entity
@Table(schema = "payroll", name = "data_validation_issue")
@Getter
@Setter
@NoArgsConstructor
public class DataValidationIssue {

    @Id
    @Column(name = "data_validation_issue_id")
    private UUID dataValidationIssueId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_code", nullable = false, columnDefinition = "payroll.issue_code")
    private IssueCode issueCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, columnDefinition = "payroll.issue_severity")
    private IssueSeverity severity;

    /** e.g. {@code "line:5012/date:2026-08-17"}. */
    @Column(name = "entity_ref")
    private String entityRef;

    @Column(name = "message", nullable = false)
    private String message;

    /** WARNING only. */
    @Column(name = "is_acknowledged", nullable = false)
    private boolean acknowledged;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledge_note")
    private String acknowledgeNote;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}

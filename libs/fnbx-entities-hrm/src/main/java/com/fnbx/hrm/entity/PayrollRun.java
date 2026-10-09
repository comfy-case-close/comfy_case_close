package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.RunStatus;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
 * One attempt at running the payroll engine for a period (spec section 6.2).
 * {@link #assertionResults} records the reconciliation check outcomes; a
 * {@link RunStatus#FAILED} run is a normal outcome, not an exception, and
 * rolls the period back to {@code DRAFT}.
 */
@Entity
@Table(schema = "payroll", name = "payroll_run")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRun {

    @Id
    @Column(name = "payroll_run_id")
    private UUID payrollRunId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.run_status")
    private RunStatus status = RunStatus.RUNNING;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "lines_calculated")
    private Integer linesCalculated;

    /** Reconciliation check outcomes (spec section 5.10, 6.2 step 11). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assertion_results", columnDefinition = "jsonb")
    private String assertionResults;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_detail", columnDefinition = "jsonb")
    private String errorDetail;
}

package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.PeriodDecisionAction;
import com.fnbx.hrm.enums.PeriodStatus;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One status change of a payroll period. Insert only: the database refuses UPDATE and
 * DELETE (trigger and revoked grant), so this entity is {@link Immutable}.
 */
@Entity
@Immutable
@Table(schema = "payroll", name = "payroll_period_decision")
@Getter
@Setter
@NoArgsConstructor
public class PayrollPeriodDecision {

    @Id
    @Column(name = "payroll_period_decision_id")
    private UUID payrollPeriodDecisionId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private PeriodDecisionAction action;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, columnDefinition = "payroll.period_status")
    private PeriodStatus oldStatus;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, columnDefinition = "payroll.period_status")
    private PeriodStatus newStatus;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "acted_by", nullable = false)
    private UUID actedBy;

    @Column(name = "acted_permission", nullable = false)
    private String actedPermission;

    @Column(name = "reason")
    private String reason;

    @Column(name = "acted_at", nullable = false, insertable = false, updatable = false)
    private Instant actedAt;
}

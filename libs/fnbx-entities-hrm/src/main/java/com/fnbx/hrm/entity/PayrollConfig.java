package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AppliesTo;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fnbx.hrm.enums.PeriodMode;
import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sheet {@code Thiet lap file}. Effective-dated: each {@link PayrollPeriod}
 * freezes the version in force at its creation ({@code configId}), so a later
 * parameter change never silently restates an already-run period.
 *
 * <p>{@link #standardHoursPerDay} alone was referenced 39 times across the
 * source workbook's formulas (formula reference section 2) - here it is one
 * column read by the engine, not 39 cells to keep in sync.
 */
@Entity
@Table(schema = "payroll", name = "payroll_config")
@Getter
@Setter
@NoArgsConstructor
public class PayrollConfig {

    @Id
    @Column(name = "payroll_config_id")
    private UUID payrollConfigId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "standard_days_per_month", nullable = false)
    private BigDecimal standardDaysPerMonth = BigDecimal.valueOf(26);

    @Column(name = "standard_hours_per_day", nullable = false)
    private BigDecimal standardHoursPerDay = BigDecimal.valueOf(8);

    @Column(name = "overtime_multiplier", nullable = false)
    private BigDecimal overtimeMultiplier = BigDecimal.valueOf(1.5);

    /**
     * DECISION[DEC-02]: tentative - the real Saturday/Sunday multiplier is not
     * yet known; at 1.0 the {@code WEEKEND_PREMIUM} component is always zero.
     * See comfy-payroll-springboot-spec.md section 14.
     */
    @Column(name = "weekend_multiplier", nullable = false)
    private BigDecimal weekendMultiplier = BigDecimal.ONE;

    @Column(name = "pay_period_start_day", nullable = false)
    private short payPeriodStartDay = 1;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "period_mode", nullable = false, columnDefinition = "payroll.period_mode")
    private PeriodMode periodMode = PeriodMode.CURRENT_MONTH;

    @Column(name = "payslip_email_subject_template")
    private String payslipEmailSubjectTemplate;

    @Column(name = "payslip_email_body_template")
    private String payslipEmailBodyTemplate;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT})
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "birthday_allowance_amount", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal birthdayAllowanceAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "birthday_allowance_applies_to", nullable = false, columnDefinition = "payroll.applies_to")
    private AppliesTo birthdayAllowanceAppliesTo = AppliesTo.BOTH;
}

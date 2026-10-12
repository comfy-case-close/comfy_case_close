package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T1/T2/T3 late-arrival penalty, effective-dated. Read by the attendance
 * parser (spec section 5.3):
 *
 * <pre>
 *   T1-h  -&gt; GREATEST(0, h - deductHours)   -- late &lt;= 15 min
 *   T2-h  -&gt; h * (1 - deductRatio)          -- late 30-45 min
 *   T3-h  -&gt; 0, voidsShift = true            -- late &gt;= 45 min
 * </pre>
 *
 * <p>{@code T3} pays nothing but still counts as a late day - counting is
 * independent of pay (formula reference section 4.1).
 */
@Entity
@Table(schema = "payroll", name = "late_penalty_rule")
@Getter
@Setter
@NoArgsConstructor
public class LatePenaltyRule {

    @Id
    @Column(name = "late_penalty_rule_id")
    private UUID latePenaltyRuleId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** T1 | T2 | T3. */
    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(name = "description")
    private String description;

    /** T1 = 1.0. */
    @Column(name = "deduct_hours", columnDefinition = "payroll.d_hours")
    private BigDecimal deductHours;

    /** T2 = 0.5. */
    @Column(name = "deduct_ratio")
    private BigDecimal deductRatio;

    /** T3 = true. */
    @Column(name = "voids_shift", nullable = false)
    private boolean voidsShift;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

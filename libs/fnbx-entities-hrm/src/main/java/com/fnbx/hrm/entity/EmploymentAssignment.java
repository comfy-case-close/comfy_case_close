package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.JobLevel;
import com.fnbx.hrm.enums.ProbationResult;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fnbx.hrm.enums.EmploymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One contract: a person, in one {@code identity.staff_position}, at one
 * default {@code identity.branch}, for one {@link EmploymentType}, with money.
 *
 * <p>This is the table ADR-0003 decision 24 keeps out of {@code identity.staff}
 * - salary and contract terms are sensitive HR data with their own DB role.
 *
 * <p>{@code (staffId, positionId, effectiveFrom..effectiveTo)} cannot overlap
 * in the database (an {@code EXCLUDE USING gist} constraint) - this is what
 * kills the source workbook's "TRUNG MASTER" duplicate-contract state and the
 * FULLTIME+PARTTIME-on-one-position {@code NA()} case (formula reference
 * sections 9, 14) structurally, instead of a report that gets checked after
 * the fact.
 */
@Entity
@Table(schema = "payroll", name = "employment_assignment")
@Getter
@Setter
@NoArgsConstructor
public class EmploymentAssignment {

    @Id
    @Column(name = "employment_assignment_id")
    private UUID employmentAssignmentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    /** {@code identity.staff_position.position_id}. */
    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    /** {@code identity.branch.branch_id}. A shared-pool branch means "may be rostered anywhere". */
    @Column(name = "default_branch_id", nullable = false)
    private UUID defaultBranchId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, columnDefinition = "payroll.employment_type")
    private EmploymentType employmentType;

    /** Required when {@link EmploymentType#FULLTIME} - enforced by {@code ck_basis}. */
    @Column(name = "monthly_base_salary", columnDefinition = "shared.d_money_nonneg")
    private BigDecimal monthlyBaseSalary;

    /** Required when {@link EmploymentType#PARTTIME}; always a typed input, never derived (R09). */
    @Column(name = "hourly_base_rate", columnDefinition = "shared.d_money_nonneg")
    private BigDecimal hourlyBaseRate;

    @Column(name = "kpi_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal kpiAllowance = BigDecimal.ZERO;

    @Column(name = "responsibility_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal responsibilityAllowance = BigDecimal.ZERO;

    /**
     * Insurance is charged on the FULL contract amount, not prorated - someone
     * working half the month is still insured on the full salary (formula
     * reference section 9, "not a bug" list in spec section 3).
     */
    @Column(name = "is_insured", nullable = false)
    private boolean insured;

    /** TRUE: the responsibility allowance is paid in full regardless of days worked ({@code FULL_SPLIT}). */
    @Column(name = "is_fixed_salary", nullable = false)
    private boolean fixedSalary;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "note")
    private String note;

    @Column(name = "supplement_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal supplementAllowance = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "job_level", columnDefinition = "payroll.job_level")
    private JobLevel jobLevel;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_kind", columnDefinition = "payroll.contract_kind")
    private ContractKind contractKind;

    @Column(name = "contract_no")
    private String contractNo;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "probation_result", columnDefinition = "payroll.probation_result")
    private ProbationResult probationResult;
}

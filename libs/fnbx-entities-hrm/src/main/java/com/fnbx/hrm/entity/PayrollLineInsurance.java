package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One (line, {@link InsuranceScheme}) row, present only when {@link #insuranceBase}
 * &gt; 0. {@link #employerAmount} / {@link #employeeAmount} are {@code GENERATED
 * ALWAYS AS (ROUND(insuranceBase * rate, 0)) STORED} - deterministic,
 * single-row, zero drift (architecture.md 2.1).
 *
 * <p>{@link #insuranceBase} is this line's share of the CONTRACT's insurance
 * base (base salary + KPI + responsibility allowance, at full contract value,
 * never prorated - formula reference section 9), split across that contract's
 * lines by {@code allowanceHours}. R08 replaces the source workbook's rule of
 * charging the whole base to "whichever row is physically first".
 */
@Entity
@Table(schema = "payroll", name = "payroll_line_insurance")
@Getter
@Setter
@NoArgsConstructor
public class PayrollLineInsurance {

    @Id
    @Column(name = "payroll_line_insurance_id")
    private UUID payrollLineInsuranceId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payroll_line_id", nullable = false)
    private UUID payrollLineId;

    @Column(name = "scheme_id", nullable = false)
    private UUID schemeId;

    @Column(name = "insurance_base", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal insuranceBase = BigDecimal.ZERO;

    /** SNAPSHOT from the effective {@link InsuranceScheme} at run time. */
    @Column(name = "employer_rate", nullable = false, columnDefinition = "payroll.d_rate")
    private BigDecimal employerRate;

    @Column(name = "employee_rate", nullable = false, columnDefinition = "payroll.d_rate")
    private BigDecimal employeeRate;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "employer_amount", insertable = false, updatable = false, columnDefinition = "shared.d_money")
    private BigDecimal employerAmount;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "employee_amount", insertable = false, updatable = false, columnDefinition = "shared.d_money")
    private BigDecimal employeeAmount;
}

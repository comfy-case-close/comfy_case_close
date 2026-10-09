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
 * SOCIAL / HEALTH / UNEMPLOYMENT (BHXH / BHYT / BHTN), effective-dated. Rates
 * are snapshotted onto {@link PayrollLineInsurance} at run time, not joined
 * live, so a statutory rate change never restates a past period.
 */
@Entity
@Table(schema = "payroll", name = "insurance_scheme")
@Getter
@Setter
@NoArgsConstructor
public class InsuranceScheme {

    @Id
    @Column(name = "insurance_scheme_id")
    private UUID insuranceSchemeId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** SOCIAL | HEALTH | UNEMPLOYMENT. */
    @Column(name = "scheme_code", nullable = false)
    private String schemeCode;

    @Column(name = "scheme_name", nullable = false)
    private String schemeName;

    /** 0.175 / 0.03 / 0.01. */
    @Column(name = "employer_rate", nullable = false, columnDefinition = "payroll.d_rate")
    private BigDecimal employerRate;

    /** 0.08 / 0.015 / 0.01. */
    @Column(name = "employee_rate", nullable = false, columnDefinition = "payroll.d_rate")
    private BigDecimal employeeRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

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
 * Lunch / housing / phone / fuel allowances - these belong to the PERSON, not
 * to any one contract (R12). Storing them here, keyed by {@code staffId} only,
 * is what makes the source workbook's "LECH PC THEO-NGUOI" warning
 * (per-person allowance mismatch, formula reference section 14 block 3)
 * structurally impossible rather than a check run after import.
 */
@Entity
@Table(schema = "payroll", name = "employee_allowance")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeAllowance {

    @Id
    @Column(name = "employee_allowance_id")
    private UUID employeeAllowanceId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "lunch_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal lunchAllowance = BigDecimal.ZERO;

    @Column(name = "housing_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Column(name = "phone_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal phoneAllowance = BigDecimal.ZERO;

    @Column(name = "fuel_allowance", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal fuelAllowance = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Multi-position payslip: one row per (period, person), consolidating every
 * {@link PayrollLine} that person has in the period - FULLTIME and PARTTIME
 * together (sheet {@code Phieu luong da vi tri}).
 *
 * <p>{@link #grossTotal} is simply the sum of each line's gross pay (R07) -
 * the source workbook's {@code (n-1) x fuel} correction is dropped because it
 * over-deducted as soon as more than one line carried a fuel allowance.
 *
 * <p>Frozen at {@link #generatedAt}: regenerating overwrites this row, but
 * once a period is {@code PAID} generation is refused (locked period).
 */
@Entity
@Table(schema = "payroll", name = "payslip")
@Getter
@Setter
@NoArgsConstructor
public class Payslip {

    @Id
    @Column(name = "payslip_id")
    private UUID payslipId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "payroll_line_count", nullable = false)
    private short payrollLineCount;

    @Column(name = "gross_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal grossTotal = BigDecimal.ZERO;

    @Column(name = "employee_insurance_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal employeeInsuranceTotal = BigDecimal.ZERO;

    @Column(name = "advance_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal advanceTotal = BigDecimal.ZERO;

    @Column(name = "deduction_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal deductionTotal = BigDecimal.ZERO;

    @Column(name = "net_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal netTotal = BigDecimal.ZERO;

    @Column(name = "late_day_total", nullable = false)
    private short lateDayTotal;

    /** Distinct {@code CP} dates. */
    @Column(name = "paid_leave_days_used", nullable = false)
    private BigDecimal paidLeaveDaysUsed = BigDecimal.ZERO;

    /** Distinct {@code KL} dates. */
    @Column(name = "unpaid_leave_days", nullable = false)
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    /** SNAPSHOT from {@code v_leave_balance} at generation time. */
    @Column(name = "annual_leave_remaining")
    private BigDecimal annualLeaveRemaining;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "late_shift_total", nullable = false)
    private short lateShiftTotal;
}

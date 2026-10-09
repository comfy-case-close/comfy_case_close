package com.fnbx.hrm.entity;

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
import java.time.Instant;
import java.util.UUID;

/**
 * One row of FULLTIME / PARTTIME: a person, in one position, at one branch,
 * for one period (spec section 5.4). The {@code snap*} fields are copied from
 * {@link EmploymentAssignment} at run time and never joined live, so an
 * ongoing contract edit cannot restate an already-calculated period.
 *
 * <h2>Two units, one column each</h2>
 * The source workbook's {@code AR} column held workdays on FULLTIME sheets but
 * raw hours on PARTTIME sheets - the single biggest trap in the original file
 * (formula reference section 8). Here both units are separate, always-present
 * columns: {@link #regularHours} and {@link #standardWorkdays}.
 *
 * <h2>R01 - weekday never compared as a string</h2>
 * {@link #standardHours}, {@link #weekendHours} and {@link #weekendDays} are
 * computed by the engine from {@code EXTRACT(ISODOW FROM work_date)}, never by
 * comparing a localized weekday label - see {@link TimesheetEntry#isWeekend()}.
 *
 * <p>No single-line recalculation exists: {@code RESP_ALW}, the 4 allowances
 * and insurance depend on sibling lines of the same person, so the engine only
 * ever runs a whole period (spec section 6.3).
 */
@Entity
@Table(schema = "payroll", name = "payroll_line")
@Getter
@Setter
@NoArgsConstructor
public class PayrollLine {

    @Id
    @Column(name = "payroll_line_id")
    private UUID payrollLineId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    /** {@code identity.branch.branch_id}. */
    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** NULL until payslips are generated for the period. */
    @Column(name = "payslip_id")
    private UUID payslipId;

    /** {@link PayrollRun} of the last successful calculation. */
    @Column(name = "calc_run_id")
    private UUID calcRunId;

    // ---- SNAPSHOT, frozen at run time --------------------------------------
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, columnDefinition = "payroll.employment_type")
    private EmploymentType employmentType;

    @Column(name = "snap_base_salary", columnDefinition = "shared.d_money")
    private BigDecimal snapBaseSalary;

    @Column(name = "snap_hourly_rate", columnDefinition = "shared.d_money")
    private BigDecimal snapHourlyRate;

    @Column(name = "snap_kpi_allowance", columnDefinition = "shared.d_money")
    private BigDecimal snapKpiAllowance;

    @Column(name = "snap_responsibility_allowance", columnDefinition = "shared.d_money")
    private BigDecimal snapResponsibilityAllowance;

    // ---- ENGINE: hours and workdays -----------------------------------------
    @Column(name = "total_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal totalHours = BigDecimal.ZERO;

    @Column(name = "standard_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal standardHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "weekend_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal weekendHours = BigDecimal.ZERO;

    @Column(name = "regular_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal regularHours = BigDecimal.ZERO;

    @Column(name = "standard_workdays", nullable = false)
    private BigDecimal standardWorkdays = BigDecimal.ZERO;

    @Column(name = "overtime_days", nullable = false)
    private BigDecimal overtimeDays = BigDecimal.ZERO;

    @Column(name = "weekend_days", nullable = false)
    private BigDecimal weekendDays = BigDecimal.ZERO;

    @Column(name = "allowance_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal allowanceHours = BigDecimal.ZERO;

    @Column(name = "allowance_workdays", nullable = false)
    private BigDecimal allowanceWorkdays = BigDecimal.ZERO;

    // ---- ENGINE: money -------------------------------------------------------
    @Column(name = "gross_pay", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal grossPay = BigDecimal.ZERO;

    /** R08: this line's share of the contract's insurance base. */
    @Column(name = "insurance_base", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal insuranceBase = BigDecimal.ZERO;

    @Column(name = "employee_insurance_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal employeeInsuranceTotal = BigDecimal.ZERO;

    @Column(name = "employer_insurance_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal employerInsuranceTotal = BigDecimal.ZERO;

    @Column(name = "deduction_total", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal deductionTotal = BigDecimal.ZERO;

    @Column(name = "net_pay", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "labor_cost", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal laborCost = BigDecimal.ZERO;

    // ---- ENGINE: attendance --------------------------------------------------
    @Column(name = "late_day_count", nullable = false)
    private short lateDayCount;

    /** {@code CP} + {@code KL} days. */
    @Column(name = "absence_day_count", nullable = false)
    private short absenceDayCount;

    @Column(name = "has_invalid_code", nullable = false)
    private boolean hasInvalidCode;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "note")
    private String note;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "late_shift_count", nullable = false)
    private short lateShiftCount;

    @Column(name = "snap_supplement_allowance", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal snapSupplementAllowance = BigDecimal.ZERO;
}

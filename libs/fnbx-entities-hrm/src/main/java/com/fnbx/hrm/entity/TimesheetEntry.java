package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.TimesheetSource;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One attendance cell. An empty cell creates no row (spec section 5.3).
 *
 * <p>{@link #rawValue} is always kept alongside the parsed result - a parser
 * bug is then re-runnable without data loss (architecture.md 3.1).
 *
 * <p>{@link #isInvalid} replaces the source workbook's {@code #MA?} sentinel:
 * an invalid cell is still SAVED (so the user does not lose their input) but
 * blocks the payroll run via {@code INVALID_ATTENDANCE_CODE}.
 *
 * <h2>R01</h2>
 * {@link #isWeekend} is {@code GENERATED ALWAYS AS (EXTRACT(ISODOW FROM
 * workDate) IN (6,7))} - never a comparison against a localized weekday
 * label string, which is exactly what made the source workbook silently
 * treat Saturday as a weekday (formula reference section 16.12).
 */
@Entity
@Table(schema = "payroll", name = "timesheet_entry")
@Getter
@Setter
@NoArgsConstructor
public class TimesheetEntry {

    @Id
    @Column(name = "timesheet_entry_id")
    private UUID timesheetEntryId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payroll_line_id", nullable = false)
    private UUID payrollLineId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** Exactly what the user typed: {@code 8}, {@code CP}, {@code T1-7,5}. */
    @Column(name = "raw_value", nullable = false)
    private String rawValue;

    @Column(name = "attendance_code_id")
    private UUID attendanceCodeId;

    @Column(name = "late_rule_id")
    private UUID lateRuleId;

    @Column(name = "declared_hours", columnDefinition = "payroll.d_hours")
    private BigDecimal declaredHours;

    /** After late penalty. */
    @Column(name = "paid_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal paidHours = BigDecimal.ZERO;

    /** {@code LEAST(declaredHours, standardHoursPerDay)}, no late penalty. */
    @Column(name = "allowance_hours", nullable = false, columnDefinition = "payroll.d_hours")
    private BigDecimal allowanceHours = BigDecimal.ZERO;

    @Column(name = "is_invalid", nullable = false)
    private boolean invalid;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "is_weekend", insertable = false, updatable = false)
    private boolean weekend;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "is_late", insertable = false, updatable = false)
    private boolean late;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "late_shifts", nullable = false)
    private short lateShifts;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, columnDefinition = "payroll.timesheet_source")
    private TimesheetSource source = TimesheetSource.MANUAL;

    @Column(name = "attendance_sheet_id")
    private UUID attendanceSheetId;

    @Column(name = "adjust_reason")
    private String adjustReason;
}

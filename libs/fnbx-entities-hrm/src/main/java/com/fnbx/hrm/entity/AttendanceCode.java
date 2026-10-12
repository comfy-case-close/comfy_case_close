package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One of the 9 attendance codes ({@code D, T, O, NN, CP, KL, TC, TS, KH},
 * stored with Vietnamese diacritics). Read by the attendance parser
 * (spec section 5.3) to turn a {@link TimesheetEntry#getRawValue()} into hours.
 */
@Entity
@Table(schema = "payroll", name = "attendance_code")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceCode {

    @Id
    @Column(name = "attendance_code_id")
    private UUID attendanceCodeId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "description", nullable = false)
    private String description;

    /** 1.0 / 0.5 / 0.0 - reporting only, not used by the paid-hours calculation. */
    @Column(name = "day_credit", nullable = false)
    private BigDecimal dayCredit = BigDecimal.ZERO;

    /** {@code CP} is paid, {@code KL} is not. */
    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    /** TRUE only for {@code CP}. */
    @Column(name = "consumes_annual_leave", nullable = false)
    private boolean consumesAnnualLeave;

    /** TRUE for {@code CP} and {@code KL}. */
    @Column(name = "counts_as_absence", nullable = false)
    private boolean countsAsAbsence;

    /** TRUE for {@code CP} and {@code KL}; part-timers reject those codes (R02 exclusion). */
    @Column(name = "fulltime_only", nullable = false)
    private boolean fulltimeOnly;
}

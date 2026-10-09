package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.PeriodStatus;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payroll month - what used to be one workbook (formula reference
 * section 3). {@link #configId} freezes the {@link PayrollConfig} version in
 * force at creation, so a later parameter edit never restates this period.
 *
 * <p>{@link #timesheetModifiedAt} is bumped on every timesheet write and
 * compared against the last successful {@link PayrollRun#getFinishedAt()};
 * when it is newer, results are stale and locking / payslip generation are
 * refused with {@code ConflictException} code {@code STALE_CALCULATION}
 * (spec section 6.4).
 */
@Entity
@Table(schema = "payroll", name = "payroll_period")
@Getter
@Setter
@NoArgsConstructor
public class PayrollPeriod {

    @Id
    @Column(name = "payroll_period_id")
    private UUID payrollPeriodId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_year", nullable = false)
    private short periodYear;

    @Column(name = "period_month", nullable = false)
    private short periodMonth;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Only correct while the period starts on day 1 - same caveat as the source workbook's {@code A1} cell. */
    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "days_in_period", insertable = false, updatable = false)
    private short daysInPeriod;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.period_status")
    private PeriodStatus status = PeriodStatus.DRAFT;

    @Column(name = "timesheet_modified_at")
    private Instant timesheetModifiedAt;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "version", nullable = false)
    private long version;

    public boolean isEditable() {
        return status.isEditable();
    }
}

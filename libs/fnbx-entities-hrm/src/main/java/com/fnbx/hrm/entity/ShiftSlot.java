package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.ShiftDayType;
import com.fnbx.hrm.enums.ShiftPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "shift_slot")
@Getter
@Setter
@NoArgsConstructor
public class ShiftSlot {

    @Id
    @Column(name = "shift_slot_id")
    private UUID shiftSlotId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "shift_period", nullable = false, columnDefinition = "payroll.shift_period")
    private ShiftPeriod shiftPeriod;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, columnDefinition = "payroll.employment_type")
    private EmploymentType employmentType;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, columnDefinition = "payroll.shift_day_type")
    private ShiftDayType dayType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "sort_order")
    private short sortOrder;

    @Column(name = "is_active")
    private boolean active;
}

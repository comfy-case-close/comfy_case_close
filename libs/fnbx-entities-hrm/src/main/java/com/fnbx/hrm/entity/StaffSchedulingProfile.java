package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "payroll", name = "staff_scheduling_profile")
@Getter
@Setter
@NoArgsConstructor
public class StaffSchedulingProfile {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "min_hours_week")
    private BigDecimal minHoursWeek;

    @Column(name = "max_hours_week")
    private BigDecimal maxHoursWeek;
}

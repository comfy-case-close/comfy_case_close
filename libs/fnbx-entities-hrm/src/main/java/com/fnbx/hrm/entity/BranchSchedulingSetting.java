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
@Table(schema = "payroll", name = "branch_scheduling_setting")
@Getter
@Setter
@NoArgsConstructor
public class BranchSchedulingSetting {

    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "min_rest_hours")
    private short minRestHours;

    @Column(name = "min_hours_part_time", nullable = false)
    private BigDecimal minHoursPartTime;

    @Column(name = "min_hours_full_time", nullable = false)
    private BigDecimal minHoursFullTime;

    @Column(name = "max_hours_week")
    private BigDecimal maxHoursWeek;
}

package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ShiftPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "shift_period_requirement")
@Getter
@Setter
@NoArgsConstructor
public class ShiftPeriodRequirement {

    @Id
    @Column(name = "shift_period_requirement_id")
    private UUID shiftPeriodRequirementId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "shift_period", nullable = false, columnDefinition = "payroll.shift_period")
    private ShiftPeriod shiftPeriod;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "min_staff")
    private short minStaff;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
}

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

/** Annual paid-leave quota for one person, one calendar year. */
@Entity
@Table(schema = "payroll", name = "employee_leave_quota")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeLeaveQuota {

    @Id
    @Column(name = "employee_leave_quota_id")
    private UUID employeeLeaveQuotaId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "leave_year", nullable = false)
    private short leaveYear;

    @Column(name = "quota_days", nullable = false)
    private BigDecimal quotaDays = BigDecimal.ZERO;

    /** Carry-in balance recorded at go-live. */
    @Column(name = "opening_used_days", nullable = false)
    private BigDecimal openingUsedDays = BigDecimal.ZERO;
}

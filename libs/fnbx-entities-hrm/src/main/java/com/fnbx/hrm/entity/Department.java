package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Grouping used for reporting only (dashboard, employee summary). NEW: neither
 * {@code identity.staff_position} nor anything else in this repo already has a
 * department concept.
 */
@Entity
@Table(schema = "payroll", name = "department")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "department_code", nullable = false)
    private String departmentCode;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}

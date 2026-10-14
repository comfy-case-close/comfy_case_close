package com.fnbx.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A department of a business ({@code identity.department}): Store Operations, Product, Marketing...
 *
 * <p>Mapped read-only ({@link Immutable}). Only identity-service writes the table, with JDBC
 * ({@code DepartmentRepository}, API {@code /api/v1/departments}); other services read it. hrm uses this entity
 * to check a position profile ({@code payroll.position_profile.department_id}) and to join department names into
 * reports. Codes are upper case: a CHECK on the table enforces it.
 */
@Entity
@Immutable
@Table(schema = "identity", name = "department")
@Getter
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

    /** identity.department.display_order is INTEGER (payroll.department had SMALLINT); ddl-auto=validate compares the JDBC types. */
    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}

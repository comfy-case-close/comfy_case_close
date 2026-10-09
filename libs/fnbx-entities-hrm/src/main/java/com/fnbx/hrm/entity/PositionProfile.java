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
 * Extension of {@code identity.staff_position} (the ERD's {@code JOB_POSITION}).
 * The position itself - code, name, active flag - is NOT duplicated here;
 * {@link #positionId} is the same id as {@code identity.staff_position.position_id}.
 * Only the two columns identity's position dictionary has no reason to carry
 * live in this table: department grouping and the trainee flag.
 */
@Entity
@Table(schema = "payroll", name = "position_profile")
@Getter
@Setter
@NoArgsConstructor
public class PositionProfile {

    /** Same value as {@code identity.staff_position.position_id} - no surrogate key. */
    @Id
    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "is_trainee", nullable = false)
    private boolean trainee = false;
}

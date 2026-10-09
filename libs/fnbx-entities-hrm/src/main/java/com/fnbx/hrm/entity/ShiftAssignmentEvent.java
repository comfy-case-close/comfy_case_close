package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AssignmentEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "shift_assignment_event")
@Getter
@Setter
@NoArgsConstructor
public class ShiftAssignmentEvent {

    @Id
    @Column(name = "shift_assignment_event_id")
    private UUID shiftAssignmentEventId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shift_schedule_id", nullable = false)
    private UUID shiftScheduleId;

    @Column(name = "shift_assignment_id", nullable = false)
    private UUID shiftAssignmentId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, columnDefinition = "payroll.assignment_event_type")
    private AssignmentEventType eventType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private String afterValue;

    @Column(name = "reason")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}

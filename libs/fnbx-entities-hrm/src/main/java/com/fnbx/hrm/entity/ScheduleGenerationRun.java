package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "schedule_generation_run")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleGenerationRun {

    @Id
    @Column(name = "schedule_generation_run_id")
    private UUID scheduleGenerationRunId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shift_schedule_id", nullable = false)
    private UUID shiftScheduleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.run_status")
    private RunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private String summary;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "undone_at")
    private Instant undoneAt;
}

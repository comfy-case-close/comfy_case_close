package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AssignmentSource;
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
@Table(schema = "payroll", name = "shift_assignment")
@Getter
@Setter
@NoArgsConstructor
public class ShiftAssignment {

    @Id
    @Column(name = "shift_assignment_id")
    private UUID shiftAssignmentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shift_schedule_id", nullable = false)
    private UUID shiftScheduleId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_slot_id", nullable = false)
    private UUID shiftSlotId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, columnDefinition = "payroll.assignment_source")
    private AssignmentSource source;

    @Column(name = "generation_run_id")
    private UUID generationRunId;

    @Column(name = "note")
    private String note;

    @Column(name = "version")
    private long version;
}

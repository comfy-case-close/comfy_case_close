package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "attendance_exception")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceException {

    @Id
    @Column(name = "shift_assignment_id")
    private UUID shiftAssignmentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.attendance_exception_status")
    private AttendanceExceptionStatus status;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "late_level", columnDefinition = "payroll.late_level")
    private LateLevel lateLevel;

    @Column(name = "payable_hours", columnDefinition = "payroll.d_hours")
    private BigDecimal payableHours;

    @Column(name = "note")
    private String note;

    @Column(name = "set_by", nullable = false)
    private UUID setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;
}

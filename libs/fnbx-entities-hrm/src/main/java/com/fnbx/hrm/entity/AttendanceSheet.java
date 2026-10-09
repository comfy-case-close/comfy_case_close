package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AttendanceSheetStatus;
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
@Table(schema = "payroll", name = "attendance_sheet")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSheet {

    @Id
    @Column(name = "attendance_sheet_id")
    private UUID attendanceSheetId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shift_schedule_id", nullable = false)
    private UUID shiftScheduleId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.attendance_sheet_status")
    private AttendanceSheetStatus status;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reopened_by")
    private UUID reopenedBy;

    @Column(name = "reopen_reason")
    private String reopenReason;

    @Column(name = "version")
    private long version;
}

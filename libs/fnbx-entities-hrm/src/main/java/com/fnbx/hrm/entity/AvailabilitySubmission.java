package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.SubmissionStatus;
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
@Table(schema = "payroll", name = "availability_submission")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilitySubmission {

    @Id
    @Column(name = "availability_submission_id")
    private UUID availabilitySubmissionId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.submission_status")
    private SubmissionStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "entered_by")
    private UUID enteredBy;

    @Column(name = "note")
    private String note;

    @Column(name = "version")
    private long version;
}

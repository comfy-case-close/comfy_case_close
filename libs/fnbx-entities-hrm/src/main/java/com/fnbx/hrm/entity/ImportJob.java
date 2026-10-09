package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ImportJobType;
import com.fnbx.hrm.enums.ImportMode;
import com.fnbx.hrm.enums.RunStatus;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One Excel import or parity-check run (spec section 8.12). {@link #mode}
 * {@code DRY_RUN} reports what would happen and writes nothing;
 * {@code COMMIT} applies it.
 */
@Entity
@Table(schema = "payroll", name = "import_job")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {

    @Id
    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, columnDefinition = "payroll.import_job_type")
    private ImportJobType jobType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, columnDefinition = "payroll.import_mode")
    private ImportMode mode;

    /** NULL for {@link ImportJobType#MASTER}. */
    @Column(name = "period_id")
    private UUID periodId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.run_status")
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "file_name")
    private String fileName;

    /** Row counts, row errors, parity differences. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private String summary;

    /** {@code identity.staff.staff_id}. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

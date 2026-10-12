package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ImportRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "import_job_row")
@Getter
@Setter
@NoArgsConstructor
public class ImportJobRow {

    @Id
    @Column(name = "import_job_row_id")
    private UUID importJobRowId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_no")
    private int rowNo;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.import_row_status")
    private ImportRowStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", nullable = false, columnDefinition = "jsonb")
    private String raw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false, columnDefinition = "jsonb")
    private String errors;

    @Column(name = "employment_assignment_id")
    private UUID employmentAssignmentId;
}

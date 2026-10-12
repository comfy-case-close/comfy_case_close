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
@Table(schema = "payroll", name = "contract_document_batch")
@Getter
@Setter
@NoArgsConstructor
public class ContractDocumentBatch {

    @Id
    @Column(name = "contract_document_batch_id")
    private UUID contractDocumentBatchId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "total")
    private int total;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.run_status")
    private RunStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}

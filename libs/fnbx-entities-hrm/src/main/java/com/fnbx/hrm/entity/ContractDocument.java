package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.ContractDocumentStatus;
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
@Table(schema = "payroll", name = "contract_document")
@Getter
@Setter
@NoArgsConstructor
public class ContractDocument {

    @Id
    @Column(name = "contract_document_id")
    private UUID contractDocumentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "employment_assignment_id", nullable = false)
    private UUID employmentAssignmentId;

    @Column(name = "contract_template_id")
    private UUID contractTemplateId;

    @Column(name = "contract_no", nullable = false)
    private String contractNo;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.contract_document_status")
    private ContractDocumentStatus status;

    @Column(name = "pdf_content")
    private byte[] pdfContent;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}

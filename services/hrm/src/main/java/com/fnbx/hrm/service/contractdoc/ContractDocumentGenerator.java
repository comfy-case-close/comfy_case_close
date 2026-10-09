package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.ContractDocument;
import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractDocumentStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ContractDocumentRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.service.contract.ContractNumberGenerator;
import com.fnbx.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractDocumentGenerator {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractTemplateResolver templateResolver;
    private final ContractDataAssembler dataAssembler;
    private final PlaceholderRenderer placeholderRenderer;
    private final ContractPdfRenderer pdfRenderer;
    private final ContractNumberGenerator numberGenerator;

    public ContractDocument generate(UUID batchId, UUID assignmentId) {
        EmploymentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(PayrollExceptions::assignmentNotFound);
        ContractTemplate template = templateResolver.resolve(assignment);
        String contractNo = contractNoOf(assignment);
        String html = placeholderRenderer.render(template.getBodyHtml(), dataAssembler.assemble(assignment, contractNo));
        byte[] pdf = pdfRenderer.render(html);

        ContractDocument document = newDocument(batchId, assignmentId, contractNo, ContractDocumentStatus.GENERATED);
        document.setContractTemplateId(template.getContractTemplateId());
        document.setPdfContent(pdf);
        document.setSha256(sha256(pdf));
        return documentRepository.save(document);
    }

    public ContractDocument recordFailure(UUID batchId, UUID assignmentId, String reason) {
        String contractNo = assignmentRepository.findById(assignmentId).map(EmploymentAssignment::getContractNo).orElse("-");
        ContractDocument document = newDocument(batchId, assignmentId, contractNo == null ? "-" : contractNo, ContractDocumentStatus.FAILED);
        document.setFailureReason(reason);
        return documentRepository.save(document);
    }

    private String contractNoOf(EmploymentAssignment assignment) {
        if (assignment.getContractNo() == null) {
            assignment.setContractNo(numberGenerator.next(assignment.getEffectiveFrom()));
            assignmentRepository.save(assignment);
        }
        return assignment.getContractNo();
    }

    private ContractDocument newDocument(UUID batchId, UUID assignmentId, String contractNo, ContractDocumentStatus status) {
        ContractDocument document = new ContractDocument();
        document.setContractDocumentId(UUID.randomUUID());
        document.setBusinessId(TenantContext.current().businessId());
        document.setBatchId(batchId);
        document.setEmploymentAssignmentId(assignmentId);
        document.setContractNo(contractNo);
        document.setStatus(status);
        document.setGeneratedBy(TenantContext.current().userId());
        document.setGeneratedAt(Instant.now());
        return document;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}

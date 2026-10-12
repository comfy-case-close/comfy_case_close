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
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractDocumentGenerator {

    private static final String PENDING_CONTRACT_NUMBER_TEXT = "(số hợp đồng được cấp khi in)";
    private static final String UNKNOWN_CONTRACT_NUMBER = "-";

    private final EmploymentAssignmentRepository assignmentRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractTemplateResolver templateResolver;
    private final ContractDataAssembler dataAssembler;
    private final PlaceholderRenderer placeholderRenderer;
    private final ContractPdfRenderer pdfRenderer;
    private final ContractNumberGenerator numberGenerator;

    public ContractDocument generate(UUID batchId, UUID assignmentId) {
        EmploymentAssignment assignment = requireAssignment(assignmentId);
        ContractTemplate template = templateResolver.resolve(assignment);
        String contractNo = ensureContractNumber(assignment);
        byte[] pdf = renderPdf(template, assignment, contractNo);

        ContractDocument document = newDocument(batchId, assignmentId, contractNo, ContractDocumentStatus.GENERATED);
        document.setContractTemplateId(template.getContractTemplateId());
        document.setPdfContent(pdf);
        document.setSha256(Digests.sha256Hex(pdf));
        return documentRepository.save(document);
    }

    public byte[] preview(UUID assignmentId) {
        EmploymentAssignment assignment = requireAssignment(assignmentId);
        ContractTemplate template = templateResolver.resolve(assignment);
        String contractNo = assignment.getContractNo() == null ? PENDING_CONTRACT_NUMBER_TEXT : assignment.getContractNo();
        return renderPdf(template, assignment, contractNo);
    }

    public ContractDocument recordFailure(UUID batchId, UUID assignmentId, String reason) {
        String contractNo = assignmentRepository.findById(assignmentId)
                .map(EmploymentAssignment::getContractNo)
                .orElse(UNKNOWN_CONTRACT_NUMBER);
        ContractDocument document = newDocument(batchId, assignmentId,
                contractNo == null ? UNKNOWN_CONTRACT_NUMBER : contractNo, ContractDocumentStatus.FAILED);
        document.setFailureReason(reason);
        return documentRepository.save(document);
    }

    private EmploymentAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId).orElseThrow(PayrollExceptions::assignmentNotFound);
    }

    private byte[] renderPdf(ContractTemplate template, EmploymentAssignment assignment, String contractNo) {
        String html = placeholderRenderer.render(template.getBodyHtml(), dataAssembler.assemble(assignment, contractNo));
        return pdfRenderer.render(html);
    }

    private String ensureContractNumber(EmploymentAssignment assignment) {
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
}

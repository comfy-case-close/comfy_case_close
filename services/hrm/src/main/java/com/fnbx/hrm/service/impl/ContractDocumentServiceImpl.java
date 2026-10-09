package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.GenerateContractsRequest;
import com.fnbx.hrm.dto.response.ContractBatchResponse;
import com.fnbx.hrm.dto.response.ContractBatchResponse.ContractDocumentResponse;
import com.fnbx.hrm.entity.ContractDocument;
import com.fnbx.hrm.entity.ContractDocumentBatch;
import com.fnbx.hrm.enums.ContractDocumentStatus;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ContractDocumentBatchRepository;
import com.fnbx.hrm.repository.ContractDocumentRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.service.ContractDocumentService;
import com.fnbx.hrm.service.contractdoc.ContractBatchRunner;
import com.fnbx.hrm.service.contractdoc.ContractPdfRenderer;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ContractDocumentServiceImpl implements ContractDocumentService {

    private final ContractDocumentBatchRepository batchRepository;
    private final ContractDocumentRepository documentRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final ContractBatchRunner batchRunner;
    private final ContractPdfRenderer pdfRenderer;
    private final TransactionTemplate transaction;
    private final BranchAccessGuard branchAccess;

    @Override
    public ContractBatchResponse generate(GenerateContractsRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        List<UUID> assignmentIds = request.employmentAssignmentIds().stream().distinct().toList();
        ContractDocumentBatch batch = transaction.execute(status -> createBatch(assignmentIds));
        batchRunner.start(batch.getContractDocumentBatchId(), assignmentIds);
        return respond(batch, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public ContractBatchResponse getBatch(UUID batchId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return respond(requireBatch(batchId), documentRepository.findByBatchIdOrderByContractNo(batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] batchPdf(UUID batchId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        requireBatch(batchId);
        List<byte[]> pdfs = documentRepository.findByBatchIdOrderByContractNo(batchId).stream()
                .filter(document -> document.getStatus() == ContractDocumentStatus.GENERATED)
                .map(ContractDocument::getPdfContent).toList();
        if (pdfs.isEmpty()) {
            throw new AppException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND);
        }
        return pdfRenderer.merge(pdfs);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] documentPdf(UUID documentId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return documentRepository.findById(documentId)
                .map(ContractDocument::getPdfContent)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractDocumentResponse> listForAssignment(UUID assignmentId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return documentRepository.findByEmploymentAssignmentIdOrderByGeneratedAtDesc(assignmentId).stream()
                .map(this::toResponse).toList();
    }

    private ContractDocumentBatch createBatch(List<UUID> assignmentIds) {
        if (assignmentRepository.findAllById(assignmentIds).size() != assignmentIds.size()) {
            throw PayrollExceptions.assignmentNotFound();
        }
        ContractDocumentBatch batch = new ContractDocumentBatch();
        batch.setContractDocumentBatchId(UUID.randomUUID());
        batch.setBusinessId(TenantContext.current().businessId());
        batch.setTotal(assignmentIds.size());
        batch.setStatus(RunStatus.RUNNING);
        batch.setCreatedBy(TenantContext.current().userId());
        batch.setCreatedAt(Instant.now());
        return batchRepository.saveAndFlush(batch);
    }

    private ContractDocumentBatch requireBatch(UUID batchId) {
        return batchRepository.findById(batchId).orElseThrow(() -> new AppException(ErrorCode.CONTRACT_BATCH_NOT_FOUND));
    }

    private ContractBatchResponse respond(ContractDocumentBatch batch, List<ContractDocument> documents) {
        long generated = documents.stream().filter(d -> d.getStatus() == ContractDocumentStatus.GENERATED).count();
        long failed = documents.size() - generated;
        return new ContractBatchResponse(batch.getContractDocumentBatchId(), batch.getTotal(), generated, failed,
                batch.getStatus().name(), batch.getCreatedAt(), batch.getFinishedAt(),
                documents.stream().map(this::toResponse).toList());
    }

    private ContractDocumentResponse toResponse(ContractDocument document) {
        return new ContractDocumentResponse(document.getContractDocumentId(), document.getEmploymentAssignmentId(),
                document.getContractNo(), document.getStatus().name(), document.getFailureReason(), document.getGeneratedAt());
    }
}

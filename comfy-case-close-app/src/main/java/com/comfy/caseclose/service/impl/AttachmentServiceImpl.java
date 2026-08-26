package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
import com.comfy.caseclose.dto.response.AttachmentUploadResponseDTO;
import com.comfy.caseclose.entity.Attachment;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.AttachmentRepository;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.service.AttachmentService;
import com.comfy.caseclose.service.AttachmentStorageService;
import com.comfy.caseclose.utils.enums.AttachmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final BranchRepository branchRepository;
    private final AttachmentStorageService attachmentStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponseDTO> getAttachmentsByCashCloseId(Long cashCloseId) {
        return attachmentRepository.findByCashCloseId(cashCloseId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Deletes the DB row and, once that commit actually lands, the underlying GCS object too —
     * previously this only deleted the row, leaving the file behind in storage forever (found in
     * the same bug hunt as the orphan-upload gap; see OrphanAttachmentCleanupJob for the other
     * half of that fix). The storage delete is deferred to afterCommit rather than done inline:
     * if the transaction rolled back for any reason, an inline delete would have already destroyed
     * a file whose DB row still exists.
     */
    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id " + attachmentId));
        attachmentRepository.delete(attachment);

        String objectKey = attachmentStorageService.objectKeyFromUrl(attachment.getFileUrl());
        if (objectKey == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                attachmentStorageService.deleteFile(objectKey);
            }
        });
    }

    @Override
    public AttachmentUploadResponseDTO uploadAttachment(MultipartFile file, AttachmentType type, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + branchId));

        AttachmentStorageService.UploadedFile uploaded =
                attachmentStorageService.uploadFile(file, branch.getBranchCode(), type.name());

        return AttachmentUploadResponseDTO.builder()
                .fileUrl(uploaded.fileUrl())
                .fileName(uploaded.fileName())
                .build();
    }

    private AttachmentResponseDTO toResponseDTO(Attachment attachment) {
        return AttachmentResponseDTO.builder()
                .id(attachment.getId())
                .type(attachment.getType().name())
                .fileUrl(attachment.getFileUrl())
                .description(attachment.getFileName())
                .cashCloseId(attachment.getCashClose().getId())
                .build();
    }
}

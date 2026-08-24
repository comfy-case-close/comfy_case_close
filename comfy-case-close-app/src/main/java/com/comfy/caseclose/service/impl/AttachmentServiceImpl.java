//package com.comfy.caseclose.service.impl;
//
//import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
//import com.comfy.caseclose.dto.response.AttachmentUploadResponseDTO;
//import com.comfy.caseclose.entity.Attachment;
//import com.comfy.caseclose.entity.Branch;
//import com.comfy.caseclose.exception.ResourceNotFoundException;
//import com.comfy.caseclose.repository.AttachmentRepository;
//import com.comfy.caseclose.repository.BranchRepository;
//import com.comfy.caseclose.service.AttachmentService;
//import com.comfy.caseclose.service.DriveStorageService;
//import com.comfy.caseclose.utils.enums.AttachmentType;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class AttachmentServiceImpl implements AttachmentService {
//
//    private final AttachmentRepository attachmentRepository;
//    private final BranchRepository branchRepository;
////    private final DriveStorageService driveStorageService;
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<AttachmentResponseDTO> getAttachmentsByCashCloseId(Long cashCloseId) {
//        return attachmentRepository.findByCashCloseId(cashCloseId)
//                .stream()
//                .map(this::toResponseDTO)
//                .toList();
//    }
//
//    @Override
//    @Transactional
//    public void deleteAttachment(Long attachmentId) {
//        Attachment attachment = attachmentRepository.findById(attachmentId)
//                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id " + attachmentId));
//        attachmentRepository.delete(attachment);
//    }
//
//    @Override
//    public AttachmentUploadResponseDTO uploadAttachment(MultipartFile file, AttachmentType type, Long branchId) {
//        Branch branch = branchRepository.findById(branchId)
//                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + branchId));
//
//        DriveStorageService.UploadedFile uploaded =
//                driveStorageService.uploadFile(file, branch.getBranchCode(), type.name());
//
//        return AttachmentUploadResponseDTO.builder()
//                .fileUrl(uploaded.fileUrl())
//                .fileName(uploaded.fileName())
//                .build();
//    }
//
//    private AttachmentResponseDTO toResponseDTO(Attachment attachment) {
//        return AttachmentResponseDTO.builder()
//                .id(attachment.getId())
//                .type(attachment.getType().name())
//                .fileUrl(attachment.getFileUrl())
//                .description(attachment.getFileName())
//                .cashCloseId(attachment.getCashClose().getId())
//                .build();
//    }
//}

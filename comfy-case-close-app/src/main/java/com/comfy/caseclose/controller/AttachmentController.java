//package com.comfy.caseclose.controller;
//
//import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
//import com.comfy.caseclose.dto.response.AttachmentUploadResponseDTO;
//import com.comfy.caseclose.service.AttachmentService;
//import com.comfy.caseclose.utils.enums.AttachmentType;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/v1")
//@RequiredArgsConstructor
//public class AttachmentController {
//
//    private final AttachmentService attachmentService;
//
//    @GetMapping("/cash-closes/{id}/attachments")
//    public ResponseEntity<List<AttachmentResponseDTO>> getAttachments(@PathVariable Long id) {
//        return ResponseEntity.ok(attachmentService.getAttachmentsByCashCloseId(id));
//    }
//
//    /**
//     * Uploads a raw attachment file (photo) to Google Drive and returns its {@code fileUrl}.
//     * Does not create an {@code Attachment} row — the client is expected to carry the returned
//     * {@code fileUrl} into {@code CashCloseSubmitRequest.attachments[].fileUrl} on submit.
//     */
//    @PostMapping(value = "/attachments/upload", consumes = "multipart/form-data")
//    @PreAuthorize("hasAnyRole('SHIFT_LEAD', 'MANAGER', 'ADMIN')")
//    public ResponseEntity<AttachmentUploadResponseDTO> uploadAttachment(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("type") AttachmentType type,
//            @RequestParam("branchId") Long branchId) {
//        return ResponseEntity.ok(attachmentService.uploadAttachment(file, type, branchId));
//    }
//
//    @DeleteMapping("/attachments/{id}")
//    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
//    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
//        attachmentService.deleteAttachment(id);
//        return ResponseEntity.noContent().build();
//    }
//}

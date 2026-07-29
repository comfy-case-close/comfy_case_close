package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
import com.comfy.caseclose.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping("/cash-closes/{id}/attachments")
    public ResponseEntity<List<AttachmentResponseDTO>> getAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.getAttachmentsByCashCloseId(id));
    }

    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return ResponseEntity.noContent().build();
    }
}

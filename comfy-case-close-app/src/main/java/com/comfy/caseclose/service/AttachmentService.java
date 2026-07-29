package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
import java.util.List;

public interface AttachmentService {

    /**
     * Get all attachments for a cash close.
     */
    List<AttachmentResponseDTO> getAttachmentsByCashCloseId(Long cashCloseId);

    /**
     * Delete an attachment.
     */
    void deleteAttachment(Long attachmentId);
}

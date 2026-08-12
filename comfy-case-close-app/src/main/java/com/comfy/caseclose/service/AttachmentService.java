package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
import com.comfy.caseclose.dto.response.AttachmentUploadResponseDTO;
import com.comfy.caseclose.utils.enums.AttachmentType;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {

    /**
     * Get all attachments for a cash close.
     */
    List<AttachmentResponseDTO> getAttachmentsByCashCloseId(Long cashCloseId);

    /**
     * Delete an attachment.
     */
    void deleteAttachment(Long attachmentId);

    /**
     * Uploads a raw file (photo) to Google Drive under {@code <root>/<year>/<month>/<branchCode>}
     * and returns its public URL. Does not persist an {@code Attachment} row — the caller is
     * expected to carry the returned {@code fileUrl} into the cash-close submit payload.
     *
     * @param file     the raw multipart file from the client
     * @param type     attachment type, used only as a file-name prefix for traceability
     * @param branchId branch the cash close belongs to, resolved to its {@code branchCode} for
     *                 the Drive folder name
     */
    AttachmentUploadResponseDTO uploadAttachment(MultipartFile file, AttachmentType type, Long branchId);
}

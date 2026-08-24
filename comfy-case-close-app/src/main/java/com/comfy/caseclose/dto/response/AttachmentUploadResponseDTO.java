package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of {@code POST /api/v1/attachments/upload}.
 *
 * <p>Upload is a two-step flow (matching the frontend's existing {@code AttachmentRequest}
 * contract): the client first uploads the raw file here to obtain {@code fileUrl}, then submits
 * the cash close with that URL inside {@code CashCloseSubmitRequest.attachments[].fileUrl}. No
 * {@code Attachment} row is created by this endpoint — the row is only created by
 * {@code CashCloseServiceImpl.persistAttachments()} when the cash close is submitted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentUploadResponseDTO {
    private String fileUrl;
    private String fileName;
}

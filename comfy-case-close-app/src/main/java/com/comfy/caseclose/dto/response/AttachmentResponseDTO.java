package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponseDTO {
    private Long id;
    private String type;  // AttachmentType
    private String fileUrl;  // canonical URL as persisted; send this back on edit, don't render it
    private String viewUrl;  // short-lived signed URL for displaying the file; null if unavailable
    private String description;
    private Long cashCloseId;
}

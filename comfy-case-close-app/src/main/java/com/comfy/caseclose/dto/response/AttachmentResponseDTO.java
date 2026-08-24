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
    private String fileUrl;
    private String description;
    private Long cashCloseId;
}

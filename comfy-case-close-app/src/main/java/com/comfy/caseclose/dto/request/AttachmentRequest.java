package com.comfy.caseclose.dto.request;

import com.comfy.caseclose.utils.enums.AttachmentType;
import com.comfy.caseclose.validation.ValidEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRequest {

    @NotBlank(message = "Type is required")
    @ValidEnum(enumClass = AttachmentType.class)
    private String type;

    @NotBlank(message = "File URL is required")
    private String fileUrl;  // URL validation can be done in service layer

    // Set from AttachmentUploadResponseDTO.fileName when the client went through
    // POST /attachments/upload first; null for a hand-typed fileUrl.
    private String fileName;

    private String description;
}

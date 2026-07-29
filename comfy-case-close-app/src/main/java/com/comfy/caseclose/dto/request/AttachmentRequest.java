package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRequest {

    @NotBlank(message = "Type is required")
    private String type;  // AttachmentType enum value

    @NotBlank(message = "File URL is required")
    private String fileUrl;  // URL validation can be done in service layer

    private String description;
}

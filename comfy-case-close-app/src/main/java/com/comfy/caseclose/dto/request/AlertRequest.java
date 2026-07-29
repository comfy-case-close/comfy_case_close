package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRequest {

    @NotBlank(message = "Channel is required")
    private String channel;  // AlertChannel enum value

    @NotBlank(message = "Severity is required")
    private String severity;  // AlertSeverity enum value

    @NotBlank(message = "Message is required")
    private String message;

    @NotEmpty(message = "At least one recipient is required")
    private List<Long> recipientUserIds;  // List of user IDs to receive the alert
}

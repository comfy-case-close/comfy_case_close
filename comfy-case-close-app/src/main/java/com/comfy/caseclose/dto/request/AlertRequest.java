package com.comfy.caseclose.dto.request;

import com.comfy.caseclose.utils.enums.AlertChannel;
import com.comfy.caseclose.utils.enums.AlertSeverity;
import com.comfy.caseclose.validation.ValidEnum;
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
    @ValidEnum(enumClass = AlertChannel.class)
    private String channel;

    @NotBlank(message = "Severity is required")
    @ValidEnum(enumClass = AlertSeverity.class)
    private String severity;

    @NotBlank(message = "Message is required")
    private String message;

    @NotEmpty(message = "At least one recipient is required")
    private List<Long> recipientUserIds;  // List of user IDs to receive the alert
}

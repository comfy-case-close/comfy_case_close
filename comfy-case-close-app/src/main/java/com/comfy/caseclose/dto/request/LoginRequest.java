package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Employee code is required")
    private String employeeCode;

    @NotBlank(message = "Passcode is required")
    @Pattern(regexp = "\\d{4}", message = "Passcode must be exactly 4 digits")
    private String passcode;

    private String userAgent;
}

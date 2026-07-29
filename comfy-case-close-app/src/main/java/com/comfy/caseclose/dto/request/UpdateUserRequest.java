package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    // Optional: omit to leave the passcode unchanged.
    @Pattern(regexp = "\\d{4}", message = "Passcode must be exactly 4 digits")
    private String passcode;

    @NotBlank(message = "Role is required")
    private String role;

    private String position;

    @Email(message = "Email must be valid")
    private String email;

    private Boolean isActive = true;

    private List<Long> branchIds;
}

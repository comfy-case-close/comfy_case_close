package com.comfy.caseclose.dto.request;

import com.comfy.caseclose.utils.enums.UserRole;
import com.comfy.caseclose.validation.ValidEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Employee code is required")
    private String employeeCode;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Passcode is required")
    @Pattern(regexp = "\\d{4}", message = "Passcode must be exactly 4 digits")
    private String passcode;

    @NotBlank(message = "Role is required")
    @ValidEnum(enumClass = UserRole.class)
    private String role;

    private String position;

    @Email(message = "Email must be valid")
    private String email;

    private Boolean isActive = true;

    private List<Long> branchIds;
}

package com.comfy.caseclose.dto.response;

import com.comfy.caseclose.utils.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class UserResponseDTO {

    private Long id;
    private String employeeCode;
    private String fullName;
    private UserRole role;
    private String position;
    private String email;
    private Boolean isActive;
    private List<Long> branchIds;
    private List<String> branchCodes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastLoginAt;
    // passcodeHash is intentionally never exposed.
}

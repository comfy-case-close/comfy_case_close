package com.comfy.caseclose.dto.response;

import com.comfy.caseclose.utils.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthUserDTO {

    private Long id;
    private String employeeCode;
    private String fullName;
    private UserRole role;
    private String position;
    private Boolean isActive;
    private List<String> branchNames;
}

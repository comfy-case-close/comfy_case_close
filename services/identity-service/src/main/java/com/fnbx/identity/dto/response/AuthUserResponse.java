package com.fnbx.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;
import com.fnbx.shared.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserResponse {

    private UUID id;
    private UUID businessId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatarUrl;
    private boolean active;
    private Map<UUID, UserRole> branchRoles;
}

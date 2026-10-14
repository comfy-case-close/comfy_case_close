package com.fnbx.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import com.fnbx.shared.security.Permission;
import java.util.Set;
import java.util.List;


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
    private String nickname;
    private boolean active;
    private Set<UUID> branchIds;
    private List<BranchAccessResponse> branchAccess;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<Permission> businessPermissions;
}

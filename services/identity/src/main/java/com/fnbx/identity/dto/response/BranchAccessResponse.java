package com.fnbx.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fnbx.shared.security.Permission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Live positions and distinct effective branch permissions. */
public record BranchAccessResponse(UUID branchId, String branchCode, String branchName,
        List<StaffPositionAccessResponse> positions,
        @JsonInclude(JsonInclude.Include.NON_NULL) Set<Permission> permissions) {
}

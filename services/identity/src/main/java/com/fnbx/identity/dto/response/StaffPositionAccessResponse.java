package com.fnbx.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fnbx.shared.security.Permission;

import java.util.Set;
import java.util.UUID;

public record StaffPositionAccessResponse(
        UUID positionId,
        String code,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) Set<Permission> permissions
) {
}

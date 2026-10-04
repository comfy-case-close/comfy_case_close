package com.fnbx.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fnbx.shared.security.Permission;

import java.util.Set;
import java.util.UUID;

public record PositionView(
        UUID positionId,
        String code,
        String name,
        boolean active,
        @JsonInclude(JsonInclude.Include.NON_NULL) Set<Permission> permissions
) {
    public PositionView(UUID positionId, String code, String name, boolean active) {
        this(positionId, code, name, active, null);
    }
}

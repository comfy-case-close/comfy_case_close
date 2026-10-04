package com.fnbx.identity.dto.response;

import com.fnbx.shared.security.Permission;

import java.util.Set;
import java.util.UUID;

public record StaffPositionAccessResponse(
        UUID positionId,
        String code,
        String name,
        Set<Permission> permissions
) {
}

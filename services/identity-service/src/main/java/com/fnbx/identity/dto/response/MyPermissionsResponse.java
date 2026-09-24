package com.fnbx.identity.dto.response;
import com.fnbx.shared.security.Permission;
import java.util.Set;
import java.util.UUID;
public record MyPermissionsResponse(UUID branchId,Set<UUID> branchIds,Set<Permission> permissions,Set<Permission> businessPermissions) {}

package com.fnbx.identity.dto.request;
import com.fnbx.shared.security.Permission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
public record PositionPermissionsRequest(@NotNull Set<@NotNull Permission> permissions) {}

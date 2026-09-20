package com.fnbx.identity.dto.request;

import com.fnbx.shared.enums.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * The role a staff member holds at one branch. Sent as a PUT on
 * {@code (branchId, staffId)}. An unchanged role is a no-op; a changed or
 * reactivated role creates a new version and preserves all previous versions.
 *
 * <p>Only an ADMIN may send {@code role = ADMIN}. An HR sending it is refused with
 * 2429; otherwise any HR could promote a colleague and be promoted back.
 */
public record AssignBranchRoleRequest(@NotNull UserRole role) {}

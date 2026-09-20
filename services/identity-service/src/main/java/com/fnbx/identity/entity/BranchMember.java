package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.shared.enums.UserRole;

/**
 * One staff member's grant at one branch, joined to enough of the person to render
 * a roster without a second query.
 *
 * <p>{@code revokedAt} is kept rather than filtered out at the repository, because
 * a revoked grant is the answer to "who used to have access", which is the whole
 * reason {@code staff_branch_role} revokes instead of deleting.
 */
public record BranchMember(UUID staffId, UUID branchId, String employeeCode, String firstName, String lastName,
                           String email, boolean staffActive, UserRole role, Instant assignedAt, Instant revokedAt) {

    public boolean live() { return revokedAt == null; }
}

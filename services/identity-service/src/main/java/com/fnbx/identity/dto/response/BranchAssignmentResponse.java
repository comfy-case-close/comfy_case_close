package com.fnbx.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One person's grant at one branch, with enough of the person attached to render a
 * roster.
 *
 * <p>{@code revokedAt} is exposed rather than filtered: a revoked grant answers
 * "who used to have access", which is why {@code staff_branch_role} revokes instead
 * of deleting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchAssignmentResponse {

    private UUID staffId;
    private UUID branchId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private boolean staffActive;
    private UUID positionId;
    private Instant assignedAt;
    private Instant revokedAt;
}

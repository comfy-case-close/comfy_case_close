package com.fnbx.identity.entity;

import com.fnbx.shared.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Assigns a staff member to a branch with a role.
 *
 * <p>Replaces the CSV {@code branchIds} column of the old spreadsheet, which
 * violated 1NF and turned "who may approve closes at branch X" into string
 * matching instead of a join.
 *
 * <p>Each row is a version with its own ID; changing a role closes the old version
 * and inserts a new one. Validity uses [assignedAt, revokedAt).
 * Revoking sets {@link #revokedAt} rather than deleting the row: an
 * investigation three months later needs to know who *used to* have access.
 */
@Entity
@Table(schema = "identity", name = "staff_branch_role")
@Getter
@Setter
@NoArgsConstructor
public class StaffBranchRole {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id") private UUID assignmentId;

    @Column(name = "staff_id")  private UUID staffId;
    @Column(name = "branch_id") private UUID branchId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "shared.user_role")
    private UserRole role;

    @Setter(AccessLevel.NONE)
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isLive() { return revokedAt == null; }

}

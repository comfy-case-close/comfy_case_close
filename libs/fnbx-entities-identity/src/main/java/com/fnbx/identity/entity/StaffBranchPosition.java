package com.fnbx.identity.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.time.Instant;
/** One immutable employment-assignment version. */
@Entity @Table(schema="identity",name="staff_branch_position")
@Getter @Setter @NoArgsConstructor
public class StaffBranchPosition {
 @Id @Column(name="assignment_id") private UUID assignmentId;
 @Column(name="staff_id",nullable=false) private UUID staffId;
 @Column(name="branch_id",nullable=false) private UUID branchId;
 @Column(name="position_id",nullable=false) private UUID positionId;
 @Column(name="business_id",nullable=false) private UUID businessId;
 @Column(name="assigned_at",nullable=false) private Instant assignedAt;
 @Column(name="revoked_at") private Instant revokedAt;
}

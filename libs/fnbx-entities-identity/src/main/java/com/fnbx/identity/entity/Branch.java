package com.fnbx.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A branch.
 *
 * <p>{@code businessId} is a bare UUID, not a {@code @ManyToOne Business}. The FK
 * still exists in the database; we simply do not build object graphs across
 * aggregates. Queries still join normally.
 */
@Entity
@Table(schema = "identity", name = "branch")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_code", nullable = false)
    private String branchCode;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(name = "address")
    private String address;

    /** Cash the owner wants left in the drawer after each shift. */
    @Column(name = "target_cash_remaining", nullable = false)
    private BigDecimal targetCashRemaining = BigDecimal.ZERO;

    @Column(name = "cash_remaining_tolerance", nullable = false)
    private BigDecimal cashRemainingTolerance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}

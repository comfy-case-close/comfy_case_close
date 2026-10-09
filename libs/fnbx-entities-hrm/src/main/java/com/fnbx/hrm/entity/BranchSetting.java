package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Extension of {@code identity.branch} (the ERD's {@code BRANCH}). The branch
 * itself lives in identity; this only adds the shared-pool / selling-store
 * distinction cash-close has no reason to know about (formula reference
 * section 11.2, glossary "Da chi nhanh" / "Cua hang ban").
 *
 * <p>A shared-pool branch is a cost bucket ({@code isSharedPool}), never a
 * selling store - its labor cost is split 1/N across the real selling stores
 * via {@link SharedCostAllocation}.
 */
@Entity
@Table(schema = "payroll", name = "branch_setting")
@Getter
@Setter
@NoArgsConstructor
public class BranchSetting {

    /** Same value as {@code identity.branch.branch_id}. */
    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "is_selling_store", nullable = false)
    private boolean sellingStore = true;

    @Column(name = "is_shared_pool", nullable = false)
    private boolean sharedPool = false;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;
}

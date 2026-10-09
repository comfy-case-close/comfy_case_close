package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manually entered revenue for a period, used only for the labor-cost-ratio
 * KPI ({@code laborCost / revenue}). {@link #branchId} {@code NULL} means
 * company-wide.
 */
@Entity
@Table(schema = "payroll", name = "branch_revenue")
@Getter
@Setter
@NoArgsConstructor
public class BranchRevenue {

    @Id
    @Column(name = "branch_revenue_id")
    private UUID branchRevenueId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    /** {@code identity.branch.branch_id}; NULL = company-wide. */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "revenue_amount", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal revenueAmount = BigDecimal.ZERO;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}

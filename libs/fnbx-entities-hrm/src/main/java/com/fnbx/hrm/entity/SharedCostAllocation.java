package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The 1/N split of a shared-pool ("Da chi nhanh") line's labor cost across
 * every active selling store (formula reference section 11.2). Only lines
 * whose {@link BranchSetting#isSharedPool()} branch is true get rows here.
 */
@Entity
@Table(schema = "payroll", name = "shared_cost_allocation")
@Getter
@Setter
@NoArgsConstructor
public class SharedCostAllocation {

    @Id
    @Column(name = "shared_cost_allocation_id")
    private UUID sharedCostAllocationId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** A line whose branch is the shared-pool bucket. */
    @Column(name = "payroll_line_id", nullable = false)
    private UUID payrollLineId;

    /** {@code identity.branch.branch_id} of one active selling store. */
    @Column(name = "target_branch_id", nullable = false)
    private UUID targetBranchId;

    /** {@code 1 / N}. */
    @Column(name = "allocation_ratio", nullable = false)
    private BigDecimal allocationRatio;

    @Column(name = "allocated_cost", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal allocatedCost = BigDecimal.ZERO;

    @Column(name = "allocated_gross", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal allocatedGross = BigDecimal.ZERO;

    @Column(name = "allocated_employer_insurance", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal allocatedEmployerInsurance = BigDecimal.ZERO;

    /** {@code BASE_PAY + OT_PAY + WEEKEND_PREMIUM} share. */
    @Column(name = "allocated_base_pay", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal allocatedBasePay = BigDecimal.ZERO;
}

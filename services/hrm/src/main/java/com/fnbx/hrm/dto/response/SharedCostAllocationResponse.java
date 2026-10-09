package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedCostAllocationResponse {
    private UUID payrollLineId;
    private UUID targetBranchId;
    private BigDecimal allocationRatio;
    private BigDecimal allocatedCost;
    private BigDecimal allocatedGross;
    private BigDecimal allocatedEmployerInsurance;
    private BigDecimal allocatedBasePay;
}

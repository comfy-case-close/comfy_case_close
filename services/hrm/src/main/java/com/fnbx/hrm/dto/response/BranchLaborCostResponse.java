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
@Builder(toBuilder = true)
public class BranchLaborCostResponse {
    private UUID branchId;
    private String employmentType;
    private long lineCount;
    private BigDecimal gross;
    private BigDecimal employerInsurance;
    private BigDecimal laborCost;
    private BigDecimal allocatedGross;
    private BigDecimal allocatedLaborCost;
    private BigDecimal payBeforeAllowance;
    private BigDecimal allowancePay;
    private BigDecimal payAfterAllowance;
}

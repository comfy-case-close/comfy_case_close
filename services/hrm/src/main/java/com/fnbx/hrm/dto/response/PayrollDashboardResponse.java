package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollDashboardResponse {
    private BigDecimal totalLaborCost;
    private BigDecimal totalGross;
    private BigDecimal employerInsurance;
    private long paidHeadcount;
    private BigDecimal costPerHead;
    private BigDecimal revenue;
    private BigDecimal laborCostRatio;
    private BigDecimal payBeforeAllowance;
    private BigDecimal allowancePay;
    private BigDecimal payAfterAllowance;
}

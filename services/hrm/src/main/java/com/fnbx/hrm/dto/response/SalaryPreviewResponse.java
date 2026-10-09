package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SalaryPreviewResponse(
        BigDecimal baseSalary,
        BigDecimal supplementAllowance,
        BigDecimal fixedTotal,
        BigDecimal dailyRate,
        InsuranceCost insurance,
        InsuranceCost insuranceBeforeSplit) {

    public record InsuranceCost(BigDecimal insuranceBase, List<SchemeCost> schemes,
                                BigDecimal employeeTotal, BigDecimal employerTotal) {}

    public record SchemeCost(String schemeCode, BigDecimal employeeAmount, BigDecimal employerAmount) {}
}

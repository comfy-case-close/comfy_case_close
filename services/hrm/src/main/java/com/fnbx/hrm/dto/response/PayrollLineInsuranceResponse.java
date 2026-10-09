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
public class PayrollLineInsuranceResponse {
    private String schemeCode;
    private BigDecimal insuranceBase;
    private BigDecimal employerRate;
    private BigDecimal employeeRate;
    private BigDecimal employerAmount;
    private BigDecimal employeeAmount;
}

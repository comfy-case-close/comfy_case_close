package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceSchemeResponse {
    private UUID insuranceSchemeId;
    private String schemeCode;
    private String schemeName;
    private BigDecimal employerRate;
    private BigDecimal employeeRate;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

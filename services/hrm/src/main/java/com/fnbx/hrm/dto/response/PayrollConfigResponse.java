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
public class PayrollConfigResponse {
    private UUID payrollConfigId;
    private LocalDate effectiveFrom;
    private BigDecimal standardDaysPerMonth;
    private BigDecimal standardHoursPerDay;
    private BigDecimal overtimeMultiplier;
    private BigDecimal weekendMultiplier;
    private short payPeriodStartDay;
    private String periodMode;
    private String payslipEmailSubjectTemplate;
    private String payslipEmailBodyTemplate;
}

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
public class EmployeePeriodSummaryResponse {
    private UUID staffId;
    private String employeeCode;
    private String employeeName;
    private BigDecimal grossTotal;
    private long lineCount;
    private long lateDays;
}

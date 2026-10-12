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
public class EmployeeAllowanceResponse {
    private UUID employeeAllowanceId;
    private UUID staffId;
    private BigDecimal lunchAllowance;
    private BigDecimal housingAllowance;
    private BigDecimal phoneAllowance;
    private BigDecimal fuelAllowance;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

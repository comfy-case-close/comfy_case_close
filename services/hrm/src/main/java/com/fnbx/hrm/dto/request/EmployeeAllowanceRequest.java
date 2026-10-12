package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST creates a new effective version. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAllowanceRequest {
    private BigDecimal lunchAllowance = BigDecimal.ZERO;
    private BigDecimal housingAllowance = BigDecimal.ZERO;
    private BigDecimal phoneAllowance = BigDecimal.ZERO;
    private BigDecimal fuelAllowance = BigDecimal.ZERO;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

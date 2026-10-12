package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EmploymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaryPreviewRequest(
        @NotNull EmploymentType employmentType,
        @NotNull @PositiveOrZero BigDecimal agreedMonthlySalary,
        @PositiveOrZero BigDecimal insuranceBase,
        @PositiveOrZero BigDecimal responsibilityAllowance,
        @PositiveOrZero BigDecimal kpiAllowance,
        boolean insured,
        LocalDate asOf) {
}

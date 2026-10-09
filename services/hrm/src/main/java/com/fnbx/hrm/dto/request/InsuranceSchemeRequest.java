package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class InsuranceSchemeRequest {
    @NotBlank
    private String schemeCode;
    @NotBlank
    private String schemeName;
    @NotNull
    private BigDecimal employerRate;
    @NotNull
    private BigDecimal employeeRate;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

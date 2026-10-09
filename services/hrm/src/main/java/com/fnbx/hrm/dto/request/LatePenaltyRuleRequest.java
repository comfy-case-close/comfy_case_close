package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST creates a new effective version; existing versions are never edited in place. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LatePenaltyRuleRequest {
    @NotBlank
    private String ruleCode;
    private String description;
    private BigDecimal deductHours;
    private BigDecimal deductRatio;
    private boolean voidsShift;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

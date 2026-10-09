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
public class LatePenaltyRuleResponse {
    private UUID latePenaltyRuleId;
    private String ruleCode;
    private String description;
    private BigDecimal deductHours;
    private BigDecimal deductRatio;
    private boolean voidsShift;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}

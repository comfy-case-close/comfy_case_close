package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskBreakdownDTO {

    private String riskLevel;
    private long count;
}

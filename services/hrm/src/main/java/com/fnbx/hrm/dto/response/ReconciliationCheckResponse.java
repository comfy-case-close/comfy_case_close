package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationCheckResponse {
    private String checkCode;
    private String description;
    private BigDecimal delta;
    private boolean ok;
}

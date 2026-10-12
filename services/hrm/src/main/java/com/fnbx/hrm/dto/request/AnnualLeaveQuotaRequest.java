package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnualLeaveQuotaRequest {
    @NotNull
    private BigDecimal quotaDays;
    private BigDecimal openingUsedDays = BigDecimal.ZERO;
}

package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code componentCode} must name a component with {@code isManualInput = true} (BONUS, ADVANCE, KPI_ADJ). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualPayItemRequest {
    @NotBlank
    private String componentCode;
    @NotNull
    private BigDecimal amount;
    private String note;
}

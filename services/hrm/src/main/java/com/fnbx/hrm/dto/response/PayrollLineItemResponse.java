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
public class PayrollLineItemResponse {
    private String componentCode;
    private String componentType;
    private BigDecimal quantity;
    private BigDecimal rate;
    private BigDecimal amount;
    private String calcNote;
}

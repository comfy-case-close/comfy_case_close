package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayslipComponentTotalResponse {
    private String componentCode;
    private String componentName;
    private String componentType;
    private BigDecimal amount;
}

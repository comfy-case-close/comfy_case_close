package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualPayItemResponse {
    private UUID payrollLineItemId;
    private UUID payrollLineId;
    private String componentCode;
    private BigDecimal amount;
    private String note;
}

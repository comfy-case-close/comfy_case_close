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
public class PayrollHistoryEntryResponse {
    private UUID periodId;
    private short periodYear;
    private short periodMonth;
    private UUID payrollLineId;
    private BigDecimal grossPay;
    private BigDecimal netPay;
}

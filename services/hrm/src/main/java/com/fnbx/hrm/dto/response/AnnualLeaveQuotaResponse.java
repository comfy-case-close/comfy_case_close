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
public class AnnualLeaveQuotaResponse {
    private UUID employeeLeaveQuotaId;
    private UUID staffId;
    private short leaveYear;
    private BigDecimal quotaDays;
    private BigDecimal openingUsedDays;
}

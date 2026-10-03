package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code v_payroll_line_hours} equivalent - live, before any payroll run. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollLineHourTotalsResponse {
    private UUID payrollLineId;
    private BigDecimal totalHours;
    private BigDecimal standardHours;
    private BigDecimal overtimeHours;
    private BigDecimal weekendHours;
    private BigDecimal standardWorkdays;
    private int lateDayCount;
    private int absenceDayCount;
    private boolean hasInvalidCode;
    private long version;
}

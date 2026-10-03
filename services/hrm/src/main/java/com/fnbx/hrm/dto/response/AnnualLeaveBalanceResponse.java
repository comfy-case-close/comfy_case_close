package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code v_leave_balance} equivalent (spec section 5.9) - a repository query, not a stored value. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnualLeaveBalanceResponse {
    private short leaveYear;
    private BigDecimal quotaDays;
    private BigDecimal openingUsedDays;
    private BigDecimal usedDays;
    private BigDecimal remainingDays;
}

package com.fnbx.cashclose.dto.request;

import com.fnbx.cashclose.enums.FundPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The active branch is taken from X-Branch-Id. */
@Data
public class FundWithdrawalRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private FundPeriod periodType = FundPeriod.ADHOC;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal systemWithdrawAmount;

    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal actualReceivedAmount;

    private String note;
}

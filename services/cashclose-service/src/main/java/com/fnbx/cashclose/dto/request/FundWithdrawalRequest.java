package com.fnbx.cashclose.dto.request;

import com.fnbx.cashclose.enums.CashPot;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Standalone transfer. Close-linked withdrawals are entered through the close request. */
@Data
public class FundWithdrawalRequest {
    @NotNull private CashPot fromPot;
    @NotNull private CashPot toPot;
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) private BigDecimal amount;
    @NotNull private UUID withdrawnBy;
    @NotNull @PastOrPresent private Instant withdrawnAt;
    private String note;
}

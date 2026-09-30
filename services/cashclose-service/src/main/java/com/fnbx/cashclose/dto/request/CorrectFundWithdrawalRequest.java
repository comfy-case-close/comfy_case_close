package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Complete replacement declaration for an existing standalone transfer. Zero cancels a mistaken declaration. */
@Data
public class CorrectFundWithdrawalRequest {
    @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) private BigDecimal amount;
    @NotNull private UUID withdrawnBy;
    @NotNull @PastOrPresent private Instant withdrawnAt;
    @NotBlank private String editReason;
    private String note;
}

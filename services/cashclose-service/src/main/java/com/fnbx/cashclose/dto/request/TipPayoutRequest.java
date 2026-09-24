package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The branch is supplied by X-Branch-Id, never by this body. */
@Data
public class TipPayoutRequest {
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    private LocalDate payoutDate;

    @Size(max = 100)
    private String recipientName;

    private String note;
}

package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashDenominationRequest {

    @NotNull(message = "Denomination value is required")
    @Positive(message = "Denomination value must be positive")
    private Long denominationValue;

    /** BigDecimal so {@link Digits} can reject fractional counts before Jackson truncates them. */
    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    @Digits(integer = 6, fraction = 0, message = "Quantity must be a whole number of notes")
    private BigDecimal quantity;

    /** Only safe after bean validation has accepted {@link #quantity} as a whole number. */
    public int quantityAsInt() {
        return quantity.intValueExact();
    }
}

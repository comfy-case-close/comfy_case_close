package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * How many notes or coins of one denomination were counted.
 *
 * <p><b>Face value, not a surrogate id.</b> {@code denomination_id} is a SMALLINT
 * identity column - an implementation detail of the lookup table that no counter
 * of cash has ever seen. The app already speaks 500000; making it learn that
 * 500000 is row 1 would couple the client to a seed order.
 *
 * <p>{@code quantity} may be zero. Sending zero is how a client says "none of
 * these left", which is different from omitting the line, and it keeps a
 * denomination grid that always posts all nine rows honest. Zero rows are dropped
 * before insert, because the table's own CHECK requires {@code quantity > 0}: a
 * row that records nothing is not a fact worth storing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DenominationCountRequest {

    @NotNull(message = "faceValue is required")
    @Positive(message = "faceValue must be positive")
    private BigDecimal faceValue;

    @PositiveOrZero(message = "quantity cannot be negative")
    private int quantity;
}

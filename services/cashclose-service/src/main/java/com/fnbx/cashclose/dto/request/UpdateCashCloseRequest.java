package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The two figures on a close that a person types. Null fields are left unchanged.
 *
 * <p>Everything else on a close is either derived ({@code cashclose.v_close_calc}),
 * snapshotted by a trigger at submit, or assembled from child rows. These two are
 * not: {@code withdrawalAmount} is a decision the shift makes about how much cash
 * to hand over, and {@code posExpectedCash} is a reading somebody takes off the POS
 * when the integration cannot take it for them.
 *
 * <p><b>There is no countedCash field, and there will not be one.</b> Counted cash
 * is {@code SUM(face_value * quantity)} over the denomination lines, computed in
 * the view. Accepting it here would recreate the drift this schema was redesigned
 * to remove - see {@link ReplaceDenominationsRequest}.
 *
 * <p>{@code posExpectedCash} is refused outright when the close's
 * {@code expectedCashSource} is POS_SYNC. A number the POS supplied is evidence;
 * letting it be typed over turns it into an opinion, and the close would still
 * claim POS_SYNC as its source.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCashCloseRequest {

    @PositiveOrZero(message = "withdrawalAmount cannot be negative")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private BigDecimal withdrawalAmount;

    @PositiveOrZero(message = "posExpectedCash cannot be negative")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private BigDecimal posExpectedCash;
}

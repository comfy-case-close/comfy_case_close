package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One counted denomination.
 *
 * <p>{@code lineTotal} is sent even though it is {@code faceValue * quantity}: the
 * client displays it on every row, and a client that multiplies for itself is a
 * second implementation of the arithmetic that produces the close's total.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DenominationLineResponse {

    private UUID lineId;
    private BigDecimal faceValue;
    private int quantity;
    private BigDecimal lineTotal;
}

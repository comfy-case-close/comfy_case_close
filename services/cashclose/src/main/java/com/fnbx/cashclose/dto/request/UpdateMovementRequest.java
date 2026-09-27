package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corrects a ledger line. Null fields are left unchanged.
 *
 * <p>Only allowed while the line is PENDING. Once a manager has approved or
 * rejected it the figures are frozen; changing them requires reopening the line
 * first, which the database records in {@code cash_movement_decision}.
 *
 * <p>This UPDATES the row rather than inserting a new one: fixing a mistyped
 * amount is a correction to the same event, and a second row would double-count
 * in every sum.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMovementRequest {

    private String kindCode;

    @Positive(message = "amount must be positive; the system applies the sign")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    /** Only for NO_CASH_FLOW kinds; omitted preserves the existing direction. */
    private DifferenceDirection differenceDirection;

    private UUID staffUserId;

    private String description;

    private UUID receiptAttachmentId;
}

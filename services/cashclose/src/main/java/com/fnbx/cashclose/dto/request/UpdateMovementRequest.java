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
 * <p>A correction is audited in cash_movement_decision and resets the line to
 * PENDING. Its cash close must still be editable.
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

    @jakarta.validation.constraints.NotBlank
    private String editReason;

    private String kindCode;

    @Positive(message = "amount must be positive; the system applies the sign")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    /** Only for NO_CASH_FLOW kinds; omitted preserves the existing direction. */
    private DifferenceDirection differenceDirection;

    private UUID staffUserId;

    private String description;

    /** An uploaded {@code files.stored_file} of kind RECEIPT or TRANSFER_PROOF. */
    private UUID receiptFileId;
}

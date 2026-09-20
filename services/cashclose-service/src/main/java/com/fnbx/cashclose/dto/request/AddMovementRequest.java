package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adds a line to the shift's cash ledger.
 *
 * <p><b>{@code amount} is always positive.</b> The sign comes from the movement
 * kind's effect type, so staff never decide whether something is negative and
 * cannot get it wrong. {@code @Positive} enforces that at the edge.
 *
 * <p>There is no vendor field. Whatever the staff want to say about who the money
 * went to belongs in {@code description} - see the note on
 * {@code CashMovement.description} for why a separate column was removed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMovementRequest {

    /** e.g. EXPENSE_SUPPLY, EOD_STAFF_PARKING, TIP_IN_DRAWER, POS_ERROR. */
    @NotBlank(message = "kindCode is required")
    private String kindCode;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive; the system applies the sign")
    private BigDecimal amount;

    /** The employee involved: who received a tip, or who topped up the drawer. */
    private UUID staffUserId;

    private String description;

    private UUID receiptAttachmentId;
}

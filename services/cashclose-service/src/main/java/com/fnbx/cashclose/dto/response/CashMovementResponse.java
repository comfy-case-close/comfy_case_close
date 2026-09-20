package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the cash ledger.
 *
 * <p>{@code signedAmount} is negative for cash out and positive for cash in;
 * {@code absAmount} is the magnitude for display. Both are sent so the client
 * never has to decide which one to trust.
 *
 * <p>{@code kindCode} plus the three arithmetic flags let the client explain the
 * line without another round trip: whether cash actually moved, whether it was
 * already inside the counted cash, and whether it leaves the drawer before hand-over.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashMovementResponse {

    private UUID movementId;

    private String kindCode;
    private String kindDisplayName;
    /** CASH_OUT | CASH_IN | NO_CASH_FLOW */
    private String effectType;

    private BigDecimal signedAmount;
    private BigDecimal absAmount;

    private UUID staffUserId;
    private String description;
    private UUID receiptAttachmentId;

    /** PENDING | APPROVED | REJECTED */
    private String approvalStatus;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionNote;

    private Instant createdAt;
}

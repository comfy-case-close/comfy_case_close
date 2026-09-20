package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a ledger line's decision history. Written by a database trigger.
 *
 * <p>{@code signedAmount} is the figure <b>this decision endorsed</b>, not the
 * current one. Comparing consecutive entries answers the question the whole table
 * exists for: was the amount changed after somebody approved it?
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementDecisionResponse {

    private UUID decisionId;
    private UUID movementId;

    private String oldStatus;
    private String newStatus;

    private BigDecimal signedAmount;
    private BigDecimal absAmount;

    private UUID decidedBy;
    private Instant decidedAt;
    private String note;
}

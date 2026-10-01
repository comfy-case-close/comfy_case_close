package com.fnbx.cashclose.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Approves or rejects a single ledger line.
 *
 * <p>The note is recorded in {@code cash_movement_decision}, so every reason
 * ever given is kept, not just the latest.
 *
 * <p>Rejecting without a reason is refused by both the service and the database:
 * a silent rejection is how a dispute becomes unresolvable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementDecisionRequest {
    private String note;
}

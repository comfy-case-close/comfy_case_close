package com.fnbx.cashclose.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Close lifecycle.
 *
 * <pre>
 *   SUBMITTED -> PENDING_REVIEW -> APPROVED -> VOIDED
 *       |              |               |
 *       +--------------+-------------> REJECTED
 *   A correction returns an APPROVED or REJECTED close to PENDING_REVIEW.
 * </pre>
 *
 * <p>A read-only mirror of the state machine in
 * {@code cashclose.fn_close_before_update}. The database is the source of truth;
 * Java checks early so the user gets a clean message, but if Java is wrong the
 * database still refuses.
 *
 * <p>Approval changes no figure. It does three things: it freezes the child rows,
 * it gates what analytics counts, and it records that a named manager signed off.
 */
public enum CloseStatus {
    SUBMITTED, PENDING_REVIEW, APPROVED, REJECTED, VOIDED;

    public Set<CloseStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED      -> EnumSet.of(PENDING_REVIEW, APPROVED, REJECTED, VOIDED);
            case PENDING_REVIEW -> EnumSet.of(APPROVED, REJECTED, VOIDED);
            case REJECTED       -> EnumSet.of(PENDING_REVIEW, VOIDED);
            case APPROVED       -> EnumSet.of(PENDING_REVIEW, VOIDED);
            case VOIDED         -> EnumSet.noneOf(CloseStatus.class);
        };
    }

    public boolean canTransitionTo(CloseStatus next) {
        return allowedNext().contains(next);
    }

    /** Child rows are still editable in these states. */
    public boolean isEditable() {
        return this == SUBMITTED || this == PENDING_REVIEW;
    }

    public boolean isTerminal() {
        return this == VOIDED;
    }
}

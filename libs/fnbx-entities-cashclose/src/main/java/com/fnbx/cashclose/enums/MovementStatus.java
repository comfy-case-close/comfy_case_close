package com.fnbx.cashclose.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Approval state of ONE ledger line - separate from the close's own status.
 *
 * <p>Three states rather than a boolean, because a manager needs to tell "the
 * employee declared nothing" apart from "the employee declared it and I have not
 * looked yet". So the screen shows three figures, not two:
 *
 * <pre>
 *   explained   = sum of APPROVED lines that affect the difference
 *   pending     = sum of PENDING lines that affect the difference
 *   unexplained = cashDifference - explained - pending
 * </pre>
 *
 * <p>A REJECTED line counts as never declared: it falls through to unexplained.
 * That is the right signal - "you told me, but I do not accept it".
 *
 * <pre>
 *   PENDING  -> APPROVED | REJECTED
 *   APPROVED -> PENDING    (reopen)
 *   REJECTED -> PENDING    (reopen, then fix and resubmit)
 * </pre>
 *
 * <p>Amount and kind may only change while PENDING. Fixing a typo updates the row
 * in place - a second row would double-count in every sum. After a decision the
 * figures are frozen and changing them needs a reopen, which is written to
 * {@code cashclose.cash_movement_decision} by a trigger, along with the amount
 * that was in force at the time.
 */
public enum MovementStatus {
    PENDING, APPROVED, REJECTED;

    public Set<MovementStatus> allowedNext() {
        return switch (this) {
            case PENDING  -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> EnumSet.of(PENDING);
        };
    }

    public boolean canTransitionTo(MovementStatus next) {
        return allowedNext().contains(next);
    }

    /** Amount and kind may only be edited while pending. */
    public boolean isEditable() { return this == PENDING; }
}

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
 *   APPROVED -> PENDING    (EDIT correction)
 *   REJECTED -> PENDING    (EDIT correction)
 * </pre>
 *
 * <p>An EDIT decision may correct a line from any status while its cash close is
 * editable. It records before/after values and resets the line to PENDING for
 * review. The movement keeps one stable ID, so corrections never double-count.
 */
public enum MovementStatus {
    PENDING, APPROVED, REJECTED;

    public Set<MovementStatus> allowedNext() {
        return switch (this) {
            case PENDING  -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> EnumSet.noneOf(MovementStatus.class);
        };
    }

    public boolean canTransitionTo(MovementStatus next) {
        return allowedNext().contains(next);
    }

}

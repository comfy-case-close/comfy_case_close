package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of the movement catalogue, as in force on a given business date.
 *
 * <p>This is what replaced four client-side enums. The old app shipped
 * {@code MOVEMENT_CATEGORIES}, {@code MOVEMENT_TYPES}, {@code DIFF_REASON_TYPES}
 * and {@code DIFF_DIRECTIONS} as hard-coded lists, which meant adding a reason
 * required a frontend release, and a close entered last year could be re-read under
 * this year's vocabulary.
 *
 * <p>The three arithmetic flags are sent so the client can explain a line as the
 * user picks it - "this reduces what you hand over", "this explains the gap against
 * POS" - without a second round trip. They are advisory for display only: the sign
 * and the sums are applied server-side from the same catalogue row.
 *
 * <p>{@code requiresNote} and {@code requiresReceipt} let the form demand what the
 * database would otherwise reject, so the user finds out before typing the rest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementKindResponse {

    private String kindCode;
    private String displayName;

    /** CASH_OUT | CASH_IN | NO_CASH_FLOW */
    private String effectType;

    private boolean affectsDifference;
    private boolean affectsRemaining;

    /** Null means this is a cash movement but not a cost. */
    private String expenseCategory;
    private String diffReasonGroup;

    private boolean requiresReceipt;
    private boolean requiresNote;
}

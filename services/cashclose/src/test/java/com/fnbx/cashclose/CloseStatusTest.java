package com.fnbx.cashclose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.fnbx.cashclose.enums.CloseStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Close state machine. This Java copy MUST match the machine in
 * {@code cashclose.fn_close_before_update}. If they diverge, the database rejects
 * transitions Java considered valid, and the user gets an opaque error.
 */
class CloseStatusTest {

    @Test @DisplayName("DRAFT can only go to SUBMITTED or VOIDED")
    void draftGoesToSubmittedOrVoided() {
        assertThat(DRAFT.allowedNext()).containsExactlyInAnyOrder(SUBMITTED, VOIDED);
    }

    @Test @DisplayName("APPROVED leaves only VOID")
    void approvedLeavesOnlyVoid() {
        assertThat(APPROVED.allowedNext()).containsExactly(VOIDED);
        assertThat(APPROVED.canTransitionTo(DRAFT)).isFalse();
        assertThat(APPROVED.canTransitionTo(REJECTED)).isFalse();
    }

    @Test @DisplayName("VOIDED is terminal")
    void voidedIsTerminal() {
        assertThat(VOIDED.allowedNext()).isEmpty();
        assertThat(VOIDED.isTerminal()).isTrue();
    }

    @Test @DisplayName("REJECTED goes back to DRAFT to be fixed")
    void rejectedGoesBackToDraft() {
        assertThat(REJECTED.canTransitionTo(DRAFT)).isTrue();
    }

    @Test @DisplayName("Only DRAFT, SUBMITTED and PENDING_REVIEW are editable")
    void onlyFirstThreeAreEditable() {
        assertThat(DRAFT.isEditable()).isTrue();
        assertThat(SUBMITTED.isEditable()).isTrue();
        assertThat(PENDING_REVIEW.isEditable()).isTrue();
        assertThat(APPROVED.isEditable()).isFalse();
        assertThat(REJECTED.isEditable()).isFalse();
        assertThat(VOIDED.isEditable()).isFalse();
    }
}

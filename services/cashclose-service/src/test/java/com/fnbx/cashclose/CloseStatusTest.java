package com.fnbx.cashclose;

import org.junit.jupiter.api.Test;
import static com.fnbx.cashclose.enums.CloseStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The Java state machine mirrors cashclose.fn_close_before_update. */
class CloseStatusTest {
    @Test void submittedCanBeReviewed() {
        assertThat(SUBMITTED.allowedNext()).containsExactlyInAnyOrder(PENDING_REVIEW, APPROVED, REJECTED, VOIDED);
    }
    @Test void correctionReturnsApprovedOrRejectedToReview() {
        assertThat(APPROVED.allowedNext()).containsExactlyInAnyOrder(PENDING_REVIEW, VOIDED);
        assertThat(REJECTED.allowedNext()).containsExactlyInAnyOrder(PENDING_REVIEW, VOIDED);
    }
    @Test void voidIsTerminal() {
        assertThat(VOIDED.allowedNext()).isEmpty();
    }
}

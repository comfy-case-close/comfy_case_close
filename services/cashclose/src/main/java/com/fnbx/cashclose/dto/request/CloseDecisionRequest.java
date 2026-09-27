package com.fnbx.cashclose.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Approves, rejects or reopens a whole close.
 *
 * <p>{@code note} is stored on the decision row in {@code cash_close_decision},
 * not on the close itself - a close that was rejected and resubmitted has several
 * comments and a single column would keep only the last.
 *
 * <p>Not annotated {@code @NotBlank}: approval may omit a note, rejection may not.
 * That asymmetry is enforced in the service, where the action is known.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseDecisionRequest {
    private String note;
}

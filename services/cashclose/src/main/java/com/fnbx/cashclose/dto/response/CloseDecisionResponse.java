package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a close's decision history, including the reviewer's comment.
 *
 * <p>Both statuses are sent because the new one alone is ambiguous:
 * REQUEST_CHANGES can leave a close in PENDING_REVIEW, so without the old status
 * a client cannot tell "changes were requested" from "nobody has touched it".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseDecisionResponse {

    private UUID decisionId;
    /** SUBMIT | APPROVE | REJECT | REQUEST_CHANGES | VOID */
    private String action;
    private UUID actedBy;
    /** Signed role used for this decision; null means legacy evidence is unavailable. */
    private String actedRole;
    private String actedPermission;
    private Instant actedAt;
    private String oldStatus;
    private String newStatus;
    private String note;
}

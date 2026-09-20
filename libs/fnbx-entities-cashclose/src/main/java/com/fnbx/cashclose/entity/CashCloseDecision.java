package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.ApprovalAction;
import com.fnbx.cashclose.enums.CloseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision ledger for a whole close. <b>Insert only.</b>
 *
 * <p>Same shape and purpose as {@link CashMovementDecision}, one level up: this
 * table holds the SEQUENCE of decisions while {@code cash_close.status} holds the
 * current one. Append-only is enforced twice - the
 * {@code shared.fn_forbid_mutation} trigger and {@code REVOKE UPDATE, DELETE} on
 * the service role.
 *
 * <h2>What close-level approval decides that line-level does not</h2>
 * Per-line decisions settle what is EXPLAINED. This one settles the RESIDUAL -
 * the part nobody declared. A shift 200,000 short with zero declared lines has no
 * line to approve; the close itself is the only decision point. It is also what
 * freezes the child rows, and what analytics filters on.
 *
 * <h2>Why both old and new status</h2>
 * <ol>
 *   <li><b>Not every action changes state.</b> REQUEST_CHANGES can leave a close
 *       in PENDING_REVIEW. With only the new status you could not tell "changes
 *       were requested" from "nobody has touched it".</li>
 *   <li><b>One row reads on its own.</b> "DRAFT to SUBMITTED" needs no context.
 *       With only the new status you would have to read the previous row, and one
 *       missing row would corrupt the whole chain.</li>
 * </ol>
 *
 * <p>This is also why close status changes are not written to
 * {@code platform.audit_log}: recording them twice duplicates the truth.
 *
 * <p>A close can go REJECTED, back to DRAFT, then APPROVED. This table keeps that
 * whole chain including every reviewer comment - which is why {@link CashClose}
 * has no {@code managerReviewNote} column: a single "latest note" would drop all
 * the others. The approver's name and time are read from the newest APPROVE row
 * here.
 */
@Entity
@Table(schema = "cashclose", name = "cash_close_decision")
@Getter
@Setter
@NoArgsConstructor
public class CashCloseDecision {

    @Id
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "cash_close_id", nullable = false) private UUID cashCloseId;
    @Column(name = "business_id", nullable = false)   private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, columnDefinition = "shared.approval_action")
    private ApprovalAction action;

    @Column(name = "acted_by", nullable = false) private UUID actedBy;

    /** Branch role in the verified access token used to authorize this decision. Null for legacy rows. */
    @Column(name = "acted_role", updatable = false)
    private String actedRole;

    @Setter(AccessLevel.NONE)
    @Column(name = "acted_at", insertable = false, updatable = false)
    private Instant actedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, columnDefinition = "shared.close_status")
    private CloseStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, columnDefinition = "shared.close_status")
    private CloseStatus newStatus;

    /** The reviewer's comment for THIS decision. Earlier ones stay on earlier rows. */
    @Column(name = "note") private String note;
}

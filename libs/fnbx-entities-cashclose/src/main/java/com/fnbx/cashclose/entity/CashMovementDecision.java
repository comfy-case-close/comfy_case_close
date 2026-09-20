package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.MovementStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Decision ledger for one cash movement line. <b>Insert only</b>, and inserted by
 * the database, never by the application.
 *
 * <p>{@link CashMovement#getApprovalStatus()} holds the CURRENT state - fast to
 * read, impossible to double-count. This table holds the SEQUENCE of decisions,
 * exactly as {@code cash_close.status} pairs with {@link CashCloseDecision} one
 * level up.
 *
 * <p>Why it exists: a manager who approves a 523,000 expense and then quietly
 * flips it to rejected would otherwise leave no trace, and that is precisely the
 * move a cash-control system exists to catch. A trigger writes the row, so
 * "we forgot to log it" is not a possible bug.
 *
 * <h2>This is an EVENT LOG, not an SCD-2 dimension</h2>
 * <table>
 *   <caption>Two different shapes for two different questions</caption>
 *   <tr><th></th><th>SCD-2 dimension</th><th>Event log (this table)</th></tr>
 *   <tr><td>Grain</td><td>one row per VERSION of an entity</td>
 *       <td>one row per THING THAT HAPPENED</td></tr>
 *   <tr><td>Question</td><td>"what did it look like on date X"</td>
 *       <td>"what happened, when, and who did it"</td></tr>
 *   <tr><td>Validity range</td><td>required, to answer point-in-time</td>
 *       <td>none - an event is instantaneous</td></tr>
 *   <tr><td>Example</td><td>{@link com.fnbx.platform.entity.MovementKind}</td>
 *       <td>this table, {@link CashCloseDecision}, {@code audit_log}</td></tr>
 * </table>
 * Hence {@link #decidedAt} rather than valid_from/valid_to, and no is_current.
 * A validity range here would model a duration the row does not have.
 *
 * <h2>Why both old and new status</h2>
 * {@link #newStatus} alone is ambiguous: PENDING can be reached from APPROVED
 * ("the manager un-approved it") or from REJECTED ("staff will fix and
 * resubmit"), and those are different stories. Keeping both also means one row
 * reads on its own, without reconstructing a chain that a single missing row
 * would corrupt.
 *
 * <h2>Why the figures are snapshotted</h2>
 * Status history alone is not enough. Approve a 523,000 line, reopen it, change
 * it to 5,230,000, approve again - the status log would read
 * PENDING, APPROVED, PENDING, APPROVED and say nothing about the amount. Each
 * decision therefore records {@link #kindSk} and {@link #signedAmount} as they
 * stood at that moment, so the ledger reads "approved at 523,000 ... approved
 * again at 5,230,000". The database also refuses to change the figures in the
 * same statement as a reopen, so the snapshot is always the approved value.
 *
 * <p>There is no {@code cashCloseId}: it is reachable through the movement. Only
 * {@link #businessId} is denormalised, because the RLS loop keys off that column,
 * and a composite FK to {@code cash_movement} keeps it honest.
 */
@Entity
@Table(schema = "cashclose", name = "cash_movement_decision")
@Getter
@Setter(AccessLevel.NONE)
@NoArgsConstructor
public class CashMovementDecision {

    @Id
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "movement_id", nullable = false) private UUID movementId;
    @Column(name = "business_id", nullable = false) private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false)
    private MovementStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private MovementStatus newStatus;

    /** The catalogue version this decision endorsed. */
    @Column(name = "kind_sk", nullable = false) private Long kindSk;

    /** The amount this decision endorsed - see "why the figures are snapshotted". */
    @Column(name = "signed_amount", nullable = false) private BigDecimal signedAmount;

    @Column(name = "decided_by") private UUID decidedBy;

    /** When the decision happened. An event has an instant, not a range. */
    @Column(name = "decided_at", insertable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "note") private String note;

    /** Positive magnitude, for display. */
    public BigDecimal absAmount() {
        return signedAmount == null ? BigDecimal.ZERO : signedAmount.abs();
    }
}

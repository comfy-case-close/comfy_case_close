package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.ExpectedCashSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A shift close - the document the whole system is built around.
 *
 * <h2>Column categories</h2>
 * <table>
 *   <caption>Three kinds of column, three write rules</caption>
 *   <tr><th>Kind</th><th>Columns</th><th>Who writes</th></tr>
 *   <tr><td>Audit</td><td>createdBy</td>
 *       <td>The application, from the signed request context</td></tr>
 *   <tr><td>Typed in</td><td>withdrawalAmount, note</td>
 *       <td>The application</td></tr>
 *   <tr><td>From POS</td><td>posExpectedCash</td>
 *       <td>The application, but locked when expectedCashSource = POS_SYNC</td></tr>
 *   <tr><td><b>Snapshot</b></td>
 *       <td>appliedDiffAllowedAbs, appliedDiffAlertAbs, late, calcVersion</td>
 *       <td>A trigger, once, at submit - immutable afterwards</td></tr>
 * </table>
 *
 * <h2>Columns that are gone</h2>
 * Every total ({@code countedCash}, {@code explainedTotal}, {@code expenseTotal},
 * {@code tipsTotal}, {@code cashDifference}, {@code unexplainedDifference},
 * {@code cashRemaining}) plus {@code riskLevel} now live in {@link CashCloseCalc},
 * read from the {@code cashclose.v_close_calc} view.
 *
 * <p>Cached totals on the parent row are a systematic source of drift: in Comfy's
 * real spreadsheet 11 of 181 closes had an explained total that disagreed with
 * their own explanation rows, and 16 of 181 had a counted-cash figure that
 * disagreed with the denomination count. What is not stored cannot drift.
 *
 * <p>{@code approvedBy} / {@code approvedAt} are gone too: they are the newest
 * APPROVE row in {@link CashCloseDecision}. {@link #status} stays denormalised
 * because it is filtered on constantly; the approver is only ever displayed.
 * Cache what you filter by, derive what you show.
 *
 * <p>{@code managerReviewNote} is gone too: a reviewer's comment belongs to the
 * DECISION that carried it, so it lives in {@link CashCloseDecision} - the same
 * place {@code decisionNote} lives one level down. A single "latest review note"
 * column silently loses every earlier comment on a close that was rejected and
 * resubmitted.
 *
 * <p>{@link #createdAt} and {@link #submittedAt} are both kept and are NOT the
 * same instant: the draft is opened at the start of the shift and submitted at the
 * end. They coincide only in the legacy spreadsheet, which had no draft stage.
 * {@link #submittedAt} is null until submit, which is exactly why it cannot be
 * renamed to createdAt.
 *
 * <h2>Sign convention</h2>
 * <pre>
 *   cashDifference = countedCash - posExpectedCash
 *                  &lt; 0  =&gt; SHORT
 *                  &gt; 0  =&gt; OVER
 * </pre>
 * Same sign space as {@code CashMovement.signedAmount} (negative = cash out), so
 * {@code unexplained = difference - explained - pending} needs no sign flip.
 * <b>This is inverted from the previous version - update the UI before deploying.</b>
 *
 * <h2>Drawer identity</h2>
 * <pre>
 *   cashRemaining = countedCash - withdrawalAmount
 *                   - sum(abs(signedAmount)) over lines that affect remaining
 * </pre>
 * This used to be an equation the database checked twice, between an app-written
 * column and a set of totals. It is now the definition, so it cannot be violated
 * and there is nothing left to check.
 *
 * <h2>{@link #calcVersion}</h2>
 * The formula version that produced the figures a manager signed off on. Changing
 * a formula means adding {@code v_close_calc_vN}, never editing the old view.
 * An APPROVED close keeps its version forever - the
 * {@code fn_close_before_update} trigger refuses to change it - so fixing a bug
 * in the formula cannot silently restate closed books.
 */
@Entity
@Table(schema = "cashclose", name = "cash_close")
@Getter
@Setter
@NoArgsConstructor
public class CashClose {

    @Id
    @Column(name = "cash_close_id")
    private UUID cashCloseId;

    @Column(name = "cash_close_code", nullable = false)
    private String cashCloseCode;

    /** Immutable after insert - a DB trigger rejects any change. */
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    /** Who opened the draft. Immutable after insert - a DB trigger rejects changes. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "branch_id", nullable = false)     private UUID branchId;
    @Column(name = "shift_type_id", nullable = false) private UUID shiftTypeId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "shared.close_status")
    private CloseStatus status = CloseStatus.DRAFT;

    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "voided_at")    private Instant voidedAt;

    // ---- expected revenue --------------------------------------------------
    @Column(name = "pos_expected_cash", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal posExpectedCash = BigDecimal.ZERO;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "expected_cash_source", nullable = false,
            columnDefinition = "shared.expected_cash_source")
    private ExpectedCashSource expectedCashSource = ExpectedCashSource.MANUAL;

    @Column(name = "pos_shift_sales_id")
    private UUID posShiftSalesId;

    // ---- typed in ----------------------------------------------------------
    @Column(name = "withdrawal_amount", nullable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal withdrawalAmount = BigDecimal.ZERO;

    // ---- snapshot: written once by a trigger at submit ---------------------
    @Setter(AccessLevel.NONE)
    @Column(name = "applied_diff_allowed_abs", insertable = false, updatable = false,
            columnDefinition = "shared.d_money_nonneg")
    private BigDecimal appliedDiffAllowedAbs;

    @Setter(AccessLevel.NONE)
    @Column(name = "applied_diff_alert_abs", insertable = false, updatable = false,
            columnDefinition = "shared.d_money_nonneg")
    private BigDecimal appliedDiffAlertAbs;

    /**
     * Not derivable: it compares submittedAt against the shift deadline and the
     * business timezone <b>as they were at submit time</b>. Moving the deadline
     * next year must not turn a punctual close into a late one.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "is_late", insertable = false, updatable = false)
    private boolean late;

    @Setter(AccessLevel.NONE)
    @Column(name = "calc_version", nullable = false)
    private String calcVersion = "v1";

    /** The submitter's own note. Reviewer comments live in {@link CashCloseDecision}. */
    @Column(name = "note") private String note;

    /** When the draft was opened - distinct from {@link #submittedAt}. */
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** POS-sourced expected cash cannot be hand-edited. */
    public boolean isExpectedCashLocked() {
        return expectedCashSource == ExpectedCashSource.POS_SYNC;
    }

    /** Child rows are still editable - mirrors {@code fn_close_child_guard}. */
    public boolean isEditable() {
        return status.isEditable();
    }
}

package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A cash close as the API exposes it.
 *
 * <p><b>These field names are the HTTP contract.</b> They are chosen for clients
 * and change only with an API version bump - never because an entity field was
 * renamed. When the two diverge, the mapper carries an explicit
 * {@code @Mapping(source = ..., target = ...)}; that one line is what keeps a
 * column rename from reaching a mobile app in the field.
 *
 * <h2>Sign convention - clients must follow</h2>
 * <pre>
 *   cashDifference = countedCash - posExpectedCash
 *                  &lt; 0  =&gt; SHORT
 *                  &gt; 0  =&gt; OVER
 * </pre>
 * The previous API used the opposite convention. Any screen that turns red on a
 * positive number needs updating.
 *
 * <h2>Three difference figures, not two</h2>
 * {@code explainedDifference} is approved, {@code pendingDifference} is declared
 * but unreviewed, {@code unexplainedDifference} is what is left. Folding pending
 * into explained makes a close look settled when nobody has looked at it.
 *
 * <p>{@code expectedCashSource} is exposed deliberately: the app must render
 * "from POS" (locked, grey) differently from "typed in" (editable, with a warning).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashCloseResponse {

    private UUID cashCloseId;
    private String cashCloseCode;
    private UUID branchId;
    private UUID shiftTypeId;
    private LocalDate businessDate;

    private String status;
    /** Derived, never stored. Must match cashclose.v_cash_close_overview. */
    private String riskLevel;
    private boolean late;
    /** Which formula version produced the figures below. */
    private String calcVersion;

    private BigDecimal posExpectedCash;
    private String expectedCashSource;
    private boolean expectedCashLocked;

    private BigDecimal countedCash;
    private BigDecimal cashDifference;
    private BigDecimal explainedDifference;
    private BigDecimal pendingDifference;
    private BigDecimal unexplainedDifference;
    private BigDecimal expenseTotal;
    private BigDecimal cashOutTotal;
    private BigDecimal cashInTotal;
    private BigDecimal tipsTotal;
    private BigDecimal tipsInDrawerTotal;
    private BigDecimal withdrawalAmount;
    private BigDecimal cashRemaining;

    /** Thresholds snapshotted at submit, so this document explains its own grading. */
    private BigDecimal appliedDiffAllowedAbs;
    private BigDecimal appliedDiffAlertAbs;

    /** The submitter's own note. Reviewer comments are in the history endpoint. */
    private String note;

    /** Who opened the draft. */
    private UUID createdBy;

    /** When the draft was opened - distinct from submittedAt. */
    private Instant createdAt;
    private Instant submittedAt;

    /** Read from the newest APPROVE row in the decision ledger. */
    private UUID approvedBy;
    private Instant approvedAt;
}

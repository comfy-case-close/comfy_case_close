package com.fnbx.cashclose.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Derived figures for one close. Read only.
 *
 * <p>Maps to the {@code cashclose.v_close_calc} view. These used to be six
 * trigger-owned columns on {@code cash_close}, written back after every child
 * change. They were removed because:
 *
 * <ul>
 *   <li><b>Cached columns drift.</b> In Comfy's real spreadsheet 11 of 181 closes
 *       had an explained total that disagreed with their own explanation rows.</li>
 *   <li><b>Formulas must be versionable.</b> A column holds one formula. A view
 *       can UNION several versions and pick per {@code cashClose.calcVersion}, so
 *       fixing a formula never restates a figure a manager already signed.</li>
 *   <li><b>The owner must be able to query it.</b> Rules living in Java are
 *       invisible to SQL, and a hand-written query would then be a second,
 *       diverging implementation.</li>
 * </ul>
 *
 * <h2>Sign convention</h2>
 * <pre>
 *   cashDifference = countedCash - posExpectedCash
 *                  &lt; 0  =&gt; SHORT
 *                  &gt; 0  =&gt; OVER
 *   unexplainedDifference = cashDifference - explainedDifference - pendingDifference
 * </pre>
 * Same sign space as {@code CashMovement.signedAmount}, so there is no inversion
 * anywhere.
 *
 * <p><b>Three figures, not two.</b> {@link #pendingDifference} is what staff have
 * declared but nobody has reviewed. Folding it into explained makes the close look
 * settled when it is not; ignoring it makes staff feel their entry was lost.
 *
 * <p>{@code @Synchronize} tells Hibernate to flush pending changes to the three
 * source tables before reading this view, so callers never need a manual
 * {@code em.flush()}.
 */
@Entity
@Immutable
@Subselect("SELECT * FROM cashclose.v_close_calc")
@Synchronize({"cashclose.cash_close",
              "cashclose.cash_movement",
              "cashclose.cash_denomination_line"})
@Getter
@NoArgsConstructor
public class CashCloseCalc {

    @Id
    @Column(name = "cash_close_id")
    private UUID cashCloseId;

    @Column(name = "business_id")  private UUID businessId;

    /** The formula version that produced these numbers. */
    @Column(name = "calc_version") private String calcVersion;

    /** Sum of face value times quantity - the only source of "how much was counted". */
    @Column(name = "counted_cash")           private BigDecimal countedCash;

    /** {@code countedCash - posExpectedCash}. Negative = short, positive = over. */
    @Column(name = "cash_difference")        private BigDecimal cashDifference;

    /** Sum of APPROVED lines that affect the difference. */
    @Column(name = "explained_difference")   private BigDecimal explainedDifference;

    /** Declared but not yet reviewed. */
    @Column(name = "pending_difference")     private BigDecimal pendingDifference;

    /** What is left - the figure a manager actually cares about. */
    @Column(name = "unexplained_difference") private BigDecimal unexplainedDifference;

    /** {@code countedCash - withdrawalAmount - sum(abs) of lines that leave the drawer}. */
    @Column(name = "cash_remaining")         private BigDecimal cashRemaining;

    /**
     * Real cost, filtered by {@code expenseCategory} rather than by cash
     * direction. Borrowing from the drawer, swapping small change and suspected
     * losses are all cash out but none of them is a cost.
     */
    @Column(name = "expense_total")          private BigDecimal expenseTotal;

    /** All cash that left the drawer, cost or not. */
    @Column(name = "cash_out_total")         private BigDecimal cashOutTotal;

    /** All cash that entered the drawer outside of sales. */
    @Column(name = "cash_in_total")          private BigDecimal cashInTotal;

    /** Total tips for the shift - used to split among staff, not to balance the drawer. */
    @Column(name = "tips_total")             private BigDecimal tipsTotal;

    /** The part of tips sitting in the drawer - this is what reduces cashRemaining. */
    @Column(name = "tips_in_drawer_total")   private BigDecimal tipsInDrawerTotal;

    /** Has anything been declared but not yet reviewed. */
    public boolean hasPendingExplanations() {
        return pendingDifference != null && pendingDifference.signum() != 0;
    }

    /** Absolute unexplained gap, for comparison against the configured thresholds. */
    public BigDecimal absUnexplained() {
        return unexplainedDifference == null ? BigDecimal.ZERO : unexplainedDifference.abs();
    }
}

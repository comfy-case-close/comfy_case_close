package com.fnbx.cashclose;

import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.enums.EffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drawer identity, and the invariants that replaced it.
 *
 * <h2>The identity moved into SQL</h2>
 * {@code cashRemaining} used to be a column the app wrote, and this class checked
 * it against {@code counted - withdrawal - eod - tipsInDrawer}. It is now that
 * expression, computed in {@code cashclose.v_close_calc}. An identity that cannot
 * be violated has nothing to test - restating it in Java would only test the test.
 *
 * <p>What is still worth checking at the Java layer are the three things that
 * replaced it, each a place where bugs actually happen:
 * <ol>
 *   <li>the sign convention - the kind decides the sign, not the person typing</li>
 *   <li>three figures (explained / pending / unexplained), not two</li>
 *   <li>SCD-2 boundaries - the upper bound is exclusive</li>
 *   <li>the per-line state machine and its editing rule</li>
 * </ol>
 *
 * <p>Figures come from Comfy's real spreadsheet. (Class name kept so git history
 * stays continuous; rename to {@code CashCloseInvariantTest} when convenient.)
 */
class DrawerIdentityTest {

    @Nested
    @DisplayName("Sign convention")
    class SignConvention {

        @Test
        @DisplayName("Expenses are negative, tips in the drawer are positive")
        void expensesNegativeTipsPositive() throws Exception {
            CashMovement spend = movement(EffectType.CASH_OUT, "-523000");
            CashMovement tip   = movement(EffectType.CASH_IN, "34000");

            assertThat(spend.getSignedAmount()).isNegative();
            assertThat(tip.getSignedAmount()).isPositive();
            assertThat(spend.isCashOut()).isTrue();
            assertThat(tip.isCashIn()).isTrue();
        }

        @Test
        @DisplayName("absAmount() is always the positive magnitude, for display")
        void absAmountAlwaysPositive() throws Exception {
            assertThat(movement(EffectType.CASH_OUT, "-523000").absAmount())
                    .isEqualByComparingTo("523000");
            assertThat(movement(EffectType.CASH_IN, "34000").absAmount())
                    .isEqualByComparingTo("34000");
        }

        @Test
        @DisplayName("A short drawer gives a NEGATIVE difference - same sign space as movements")
        void shortDrawerIsNegative() throws Exception {
            // Real shift: counted 6,500,000 against POS expected 8,000,000
            CashCloseCalc k = calc("6500000", "-1500000", "-500000", "0", "-1000000", "3500000");

            assertThat(k.getCashDifference()).isNegative();
            assertThat(k.getCountedCash().subtract(new BigDecimal("8000000")))
                    .isEqualByComparingTo(k.getCashDifference());
        }

        @Test
        @DisplayName("An over drawer gives a POSITIVE difference - 34k tip left inside")
        void overDrawerIsPositive() throws Exception {
            // Real shift: counted 8,180,000, POS expected 8,146,000, 34,000 tip inside
            CashCloseCalc k = calc("8180000", "34000", "34000", "0", "0", "8146000");

            assertThat(k.getCashDifference()).isPositive();
            assertThat(k.getUnexplainedDifference()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Three figures, not two")
    class ThreeFigures {

        @Test
        @DisplayName("unexplained = difference - explained - pending, with no sign flip")
        void unexplainedIsPlainSubtraction() throws Exception {
            CashCloseCalc k = calc("6500000", "-1500000", "-500000", "-300000", "-700000", "3500000");

            BigDecimal expected = k.getCashDifference()
                    .subtract(k.getExplainedDifference())
                    .subtract(k.getPendingDifference());
            assertThat(k.getUnexplainedDifference()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Declared but unreviewed is reported separately, not folded into explained")
        void pendingIsNotExplained() throws Exception {
            CashCloseCalc k = calc("6500000", "-1500000", "-500000", "-300000", "-700000", "3500000");

            assertThat(k.hasPendingExplanations()).isTrue();
            assertThat(k.getExplainedDifference()).isEqualByComparingTo("-500000");
        }

        @Test
        @DisplayName("Nothing waiting means pending is zero")
        void nothingPending() throws Exception {
            assertThat(calc("8180000", "34000", "34000", "0", "0", "8146000")
                    .hasPendingExplanations()).isFalse();
        }

        @Test
        @DisplayName("absUnexplained is never negative, whether short or over")
        void absUnexplainedNeverNegative() throws Exception {
            assertThat(calc("1", "-1", "0", "0", "-900000", "1").absUnexplained())
                    .isEqualByComparingTo("900000");
            assertThat(calc("1", "1", "0", "0", "900000", "1").absUnexplained())
                    .isEqualByComparingTo("900000");
        }
    }

    @Nested
    @DisplayName("SCD-2 validity boundary")
    class Scd2Boundary {

        @Test
        @DisplayName("validFrom is inclusive, validTo exclusive - the changeover day belongs to the new version")
        void boundaryIsHalfOpen() throws Exception {
            MovementKind oldVersion = kind("2026-01-01", "2027-03-01");
            MovementKind newVersion = kind("2027-03-01", null);

            assertThat(oldVersion.isEffectiveOn(LocalDate.parse("2027-02-28"))).isTrue();
            assertThat(oldVersion.isEffectiveOn(LocalDate.parse("2027-03-01"))).isFalse();
            assertThat(newVersion.isEffectiveOn(LocalDate.parse("2027-03-01"))).isTrue();
        }

        @Test
        @DisplayName("An open version (validTo null) stays in force indefinitely")
        void openVersionNeverExpires() throws Exception {
            MovementKind current = kind("2027-03-01", null);
            assertThat(current.isEffectiveOn(LocalDate.parse("2099-12-31"))).isTrue();
            assertThat(current.isEffectiveOn(LocalDate.parse("2027-02-28"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Per-line state machine")
    class LineStateMachine {

        @Test
        @DisplayName("A new line is PENDING, never APPROVED")
        void newLineIsPending() throws Exception {
            assertThat(movement(EffectType.CASH_IN, "34000").getApprovalStatus())
                    .isEqualTo(MovementStatus.PENDING);
        }

        @Test
        @DisplayName("approve sets all three decision fields - the DB requires them together")
        void approveSetsAllDecisionFields() throws Exception {
            CashMovement m = movement(EffectType.CASH_OUT, "-523000");
            UUID by = UUID.randomUUID();
            Instant at = Instant.now();

            m.approve(by, at, "supplier invoice attached");

            assertThat(m.getApprovalStatus()).isEqualTo(MovementStatus.APPROVED);
            assertThat(m.getDecidedBy()).isEqualTo(by);
            assertThat(m.getDecidedAt()).isEqualTo(at);
            assertThat(m.getDecisionNote()).isEqualTo("supplier invoice attached");
        }

        @Test
        @DisplayName("reject records who and why - knowing who rejected matters most")
        void rejectRecordsWhoAndWhy() throws Exception {
            CashMovement m = movement(EffectType.CASH_OUT, "-523000");
            UUID by = UUID.randomUUID();

            m.reject(by, Instant.now(), "no receipt");

            assertThat(m.getApprovalStatus()).isEqualTo(MovementStatus.REJECTED);
            assertThat(m.getDecidedBy()).isEqualTo(by);
            assertThat(m.getDecisionNote()).isEqualTo("no receipt");
        }

        @Test
        @DisplayName("reopen clears the decision so the figures become editable again")
        void reopenClearsDecision() throws Exception {
            CashMovement m = movement(EffectType.CASH_OUT, "-523000");
            m.approve(UUID.randomUUID(), Instant.now(), "ok");
            assertThat(m.isEditable()).isFalse();

            m.reopen("wrong amount, staff will fix");

            assertThat(m.getApprovalStatus()).isEqualTo(MovementStatus.PENDING);
            assertThat(m.getDecidedBy()).isNull();
            assertThat(m.getDecidedAt()).isNull();
            assertThat(m.isEditable()).isTrue();
        }

        @Test
        @DisplayName("Only reopening is allowed once a line has been decided")
        void decidedLinesOnlyReopen() {
            assertThat(MovementStatus.PENDING.canTransitionTo(MovementStatus.APPROVED)).isTrue();
            assertThat(MovementStatus.PENDING.canTransitionTo(MovementStatus.REJECTED)).isTrue();
            assertThat(MovementStatus.APPROVED.canTransitionTo(MovementStatus.PENDING)).isTrue();
            assertThat(MovementStatus.REJECTED.canTransitionTo(MovementStatus.PENDING)).isTrue();

            assertThat(MovementStatus.APPROVED.canTransitionTo(MovementStatus.REJECTED)).isFalse();
            assertThat(MovementStatus.REJECTED.canTransitionTo(MovementStatus.APPROVED)).isFalse();
        }
    }

    // ------------------------------------------------------------------------
    // These classes have no public setters for DB-generated values, so the tests
    // build them with reflection - same approach as the previous version.
    // ------------------------------------------------------------------------

    private static CashMovement movement(EffectType effect, String signed) throws Exception {
        CashMovement m = new CashMovement();
        m.setEffectType(effect);
        m.setSignedAmount(new BigDecimal(signed));
        return m;
    }

    private static CashCloseCalc calc(String counted, String difference,
                                      String explained, String pending,
                                      String unexplained, String remaining) throws Exception {
        CashCloseCalc k = new CashCloseCalc();
        set(k, "countedCash", new BigDecimal(counted));
        set(k, "cashDifference", new BigDecimal(difference));
        set(k, "explainedDifference", new BigDecimal(explained));
        set(k, "pendingDifference", new BigDecimal(pending));
        set(k, "unexplainedDifference", new BigDecimal(unexplained));
        set(k, "cashRemaining", new BigDecimal(remaining));
        return k;
    }

    private static MovementKind kind(String from, String to) throws Exception {
        MovementKind k = new MovementKind();
        k.setValidFrom(LocalDate.parse(from));
        if (to != null) k.setValidTo(LocalDate.parse(to));
        return k;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

package com.fnbx.cashclose.service;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.enums.RiskLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * A close plus its derived figures and its approver.
 *
 * <p>Needed because {@link CashClose} no longer carries any total - they live in
 * {@link CashCloseCalc}, read from a view - and no longer carries the approver
 * either, which comes from the newest APPROVE row in the approval ledger. The
 * service assembles all three once instead of leaving every caller to hunt.
 *
 * <p>{@link #riskLevel} is likewise not stored: it is a function of the
 * unexplained difference and the two thresholds snapshotted at submit. Because
 * those are frozen per close, the value is stable over time even if the owner
 * later changes the config. <b>This formula must match
 * {@code cashclose.v_cash_close_overview}</b> - change one without the other and
 * the dashboard and the API will report different risk for the same close.
 */
public record CashCloseView(CashClose close,
                            CashCloseCalc calc,
                            RiskLevel riskLevel,
                            UUID approvedBy,
                            Instant approvedAt) {

    public static CashCloseView of(CashClose close, CashCloseCalc calc,
                                   UUID approvedBy, Instant approvedAt) {
        return new CashCloseView(close, calc, deriveRisk(close, calc), approvedBy, approvedAt);
    }

    private static RiskLevel deriveRisk(CashClose close, CashCloseCalc calc) {
        if (calc == null || close.getAppliedDiffAlertAbs() == null) return null;
        var unexplained = calc.absUnexplained();
        if (unexplained.compareTo(close.getAppliedDiffAlertAbs()) > 0)   return RiskLevel.HIGH;
        if (unexplained.compareTo(close.getAppliedDiffAllowedAbs()) > 0) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}

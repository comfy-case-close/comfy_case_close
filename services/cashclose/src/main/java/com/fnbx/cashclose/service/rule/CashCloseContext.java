package com.fnbx.cashclose.service.rule;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.shared.enums.BusinessType;

import java.math.BigDecimal;

/**
 * What a rule plug-in needs in order to judge a close.
 *
 * <p>Two clearly separated halves:
 * <ul>
 *   <li>{@link #cashClose()} - the INPUT columns: expected revenue, withdrawal,
 *       POS or manual source, late or not</li>
 *   <li>{@link #calc()} - the DERIVED figures, read from
 *       {@code cashclose.v_close_calc} under the close's own {@code calcVersion}</li>
 * </ul>
 *
 * <p>A plug-in must never re-sum anything from the ledger itself. If it did, each
 * business type would grow its own arithmetic and the number in an alert would
 * stop matching the number on the dashboard.
 */
public record CashCloseContext(
        CashClose cashClose,
        CashCloseCalc calc,
        BusinessType businessType,
        BigDecimal diffAllowedAbs,
        BigDecimal diffAlertAbs,
        BigDecimal expenseAlertAbs
) {
    /** Absolute unexplained gap - shared helper so every plug-in reads it the same way. */
    public BigDecimal absUnexplained() {
        return calc == null ? BigDecimal.ZERO : calc.absUnexplained();
    }
}

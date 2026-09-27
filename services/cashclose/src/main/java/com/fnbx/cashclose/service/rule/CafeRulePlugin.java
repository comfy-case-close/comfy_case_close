package com.fnbx.cashclose.service.rule;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.enums.ExpectedCashSource;
import com.fnbx.cashclose.enums.RiskLevel;
import com.fnbx.shared.enums.BusinessType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Rules for a coffee shop. The baseline set, also used as the fallback. */
@Component
public class CafeRulePlugin implements CashCloseRulePlugin {

    @Override public BusinessType businessType() { return BusinessType.CAFE; }

    @Override
    public ValidationResult validate(CashCloseContext ctx) {
        CashClose c = ctx.cashClose();
        CashCloseCalc k = ctx.calc();
        ValidationResult r = ValidationResult.ok();

        // No drawer identity check: cashRemaining IS the expression
        // counted - withdrawal - sum(lines that leave the drawer), computed in the
        // view. An identity that cannot be violated has nothing to report.

        if (k != null && k.hasPendingExplanations()) {
            r.error("%s is still awaiting review - approve or reject each line before closing"
                    .formatted(k.getPendingDifference().abs()));
        }

        BigDecimal unexplained = ctx.absUnexplained();
        if (unexplained.compareTo(ctx.diffAllowedAbs()) > 0) {
            r.warn("Unexplained difference %s exceeds the allowed %s - a reason is needed"
                    .formatted(unexplained, ctx.diffAllowedAbs()));
        }

        // Hand-typed expected revenue is the weak point of the whole control
        if (c.getExpectedCashSource() == ExpectedCashSource.MANUAL) {
            r.warn("Expected revenue was TYPED IN, not read from the POS. "
                 + "The yardstick was supplied by the person counting the cash.");
        }

        return r;
    }

    @Override
    public RiskLevel assessRisk(CashCloseContext ctx) {
        CashClose c = ctx.cashClose();
        BigDecimal unexplained = ctx.absUnexplained();

        if (unexplained.compareTo(ctx.diffAlertAbs()) > 0) return RiskLevel.HIGH;
        if (unexplained.compareTo(ctx.diffAllowedAbs()) > 0) return RiskLevel.MEDIUM;
        if (c.isLate()) return RiskLevel.MEDIUM;
        if (c.getExpectedCashSource() == ExpectedCashSource.MANUAL) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}

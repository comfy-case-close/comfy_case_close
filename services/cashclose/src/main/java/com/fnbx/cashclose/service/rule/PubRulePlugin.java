package com.fnbx.cashclose.service.rule;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.enums.RiskLevel;
import com.fnbx.shared.enums.BusinessType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rules for a pub or bar. Two practical differences from a coffee shop:
 *
 * <ul>
 *   <li>Night revenue is far higher, so an absolute threshold is not enough - a
 *       200k gap on 50M of revenue is nothing like a 200k gap on 3M.</li>
 *   <li>Tips are large and often left in the drawer, so an unusual tip ratio is
 *       worth watching.</li>
 * </ul>
 *
 * <p>This is why the Microkernel pattern is here: neither rule can live alongside
 * the cafe rules without an {@code if} statement leaking into the core.
 */
@Component
public class PubRulePlugin implements CashCloseRulePlugin {

    /** A gap above 0.5% of revenue is notable even under the absolute threshold. */
    private static final BigDecimal RELATIVE_THRESHOLD = new BigDecimal("0.005");

    /** Tips above 15% of cash revenue are unusual for a Vietnamese pub. */
    private static final BigDecimal TIP_RATIO_ALERT = new BigDecimal("0.15");

    @Override public BusinessType businessType() { return BusinessType.PUB; }

    @Override
    public ValidationResult validate(CashCloseContext ctx) {
        CashClose c = ctx.cashClose();
        CashCloseCalc k = ctx.calc();
        ValidationResult r = ValidationResult.ok();

        if (k != null && k.hasPendingExplanations()) {
            r.error("%s is still awaiting review - approve or reject each line before closing"
                    .formatted(k.getPendingDifference().abs()));
        }

        BigDecimal expected = c.getPosExpectedCash();
        BigDecimal unexplained = ctx.absUnexplained();

        if (expected.signum() > 0) {
            BigDecimal ratio = unexplained.divide(expected, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(RELATIVE_THRESHOLD) > 0) {
                r.warn("Unexplained difference is %s%% of revenue - over the pub relative threshold"
                        .formatted(ratio.movePointRight(2)));
            }
            if (k != null && k.getTipsTotal() != null) {
                BigDecimal tipRatio = k.getTipsTotal().divide(expected, 4, RoundingMode.HALF_UP);
                if (tipRatio.compareTo(TIP_RATIO_ALERT) > 0) {
                    r.warn("Tip ratio %s%% is unusually high - check how tips were classified"
                            .formatted(tipRatio.movePointRight(2)));
                }
            }
        }
        return r;
    }

    @Override
    public RiskLevel assessRisk(CashCloseContext ctx) {
        CashClose c = ctx.cashClose();
        BigDecimal expected = c.getPosExpectedCash();
        BigDecimal unexplained = ctx.absUnexplained();

        if (unexplained.compareTo(ctx.diffAlertAbs()) > 0) return RiskLevel.HIGH;

        if (expected.signum() > 0) {
            BigDecimal ratio = unexplained.divide(expected, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(RELATIVE_THRESHOLD) > 0) return RiskLevel.HIGH;
        }
        if (unexplained.compareTo(ctx.diffAllowedAbs()) > 0) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}

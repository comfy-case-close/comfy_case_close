package com.fnbx.hrm.service.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record LatePenalty(String ruleCode, BigDecimal deductHours, BigDecimal deductRatio, boolean voidsShift) {

    public BigDecimal workedHours(BigDecimal scheduledHours) {
        if (voidsShift) {
            return BigDecimal.ZERO;
        }
        BigDecimal hours = deductHours == null ? BigDecimal.ZERO : deductHours;
        BigDecimal share = deductRatio == null ? BigDecimal.ZERO : scheduledHours.multiply(deductRatio);
        return scheduledHours.subtract(hours).subtract(share).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}

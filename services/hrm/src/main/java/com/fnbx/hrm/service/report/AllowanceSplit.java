package com.fnbx.hrm.service.report;

import java.math.BigDecimal;

public record AllowanceSplit(BigDecimal payBeforeAllowance, BigDecimal allowancePay) {

    public static final AllowanceSplit ZERO = new AllowanceSplit(BigDecimal.ZERO, BigDecimal.ZERO);

    public BigDecimal payAfterAllowance() {
        return payBeforeAllowance.add(allowancePay);
    }

    public AllowanceSplit plus(PayBucket bucket, BigDecimal amount) {
        return switch (bucket) {
            case BEFORE_ALLOWANCE -> new AllowanceSplit(payBeforeAllowance.add(amount), allowancePay);
            case ALLOWANCE -> new AllowanceSplit(payBeforeAllowance, allowancePay.add(amount));
            case OTHER -> this;
        };
    }
}

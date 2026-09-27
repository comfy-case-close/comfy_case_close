package com.fnbx.cashclose.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TipPayoutResultResponse(TipJarResponse.Payout payout,
                                      BigDecimal balanceAfter,
                                      List<Warning> warnings) {
    public record Warning(String code, BigDecimal amount, BigDecimal limit) {}
}

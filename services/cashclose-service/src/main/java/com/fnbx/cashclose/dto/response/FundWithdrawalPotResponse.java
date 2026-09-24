package com.fnbx.cashclose.dto.response;

import com.fnbx.cashclose.enums.FundPeriod;
import com.fnbx.cashclose.enums.FundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One selected branch's accumulated withdrawals and reconciliation log. */
public record FundWithdrawalPotResponse(Scope scope, Summary summary,
                                        List<Source> sources, List<Log> logs,
                                        List<Warning> warnings) {
    public record Scope(UUID branchId, String branchCode, String branchLabel,
                        LocalDate fromDate, LocalDate toDate, FundPeriod periodType) {}
    public record Summary(BigDecimal generatedPot, BigDecimal alreadyWithdrawn,
                          BigDecimal actualReceived, BigDecimal variance,
                          BigDecimal remainingPot, long sourceCount, long logCount) {}
    public record Source(UUID cashCloseId, String cashCloseCode, LocalDate businessDate,
                         BigDecimal withdrawalAmount, BigDecimal cashRemaining) {}
    public record Log(UUID id, String code, LocalDate periodFrom, LocalDate periodTo,
                      FundPeriod periodType, BigDecimal systemPotBefore,
                      BigDecimal systemWithdrawAmount, BigDecimal actualReceivedAmount,
                      BigDecimal varianceAmount, BigDecimal systemPotAfter,
                      String note, FundStatus status, UUID createdBy, Instant createdAt) {}
    public record Warning(String code, BigDecimal amount, BigDecimal limit) {}
}

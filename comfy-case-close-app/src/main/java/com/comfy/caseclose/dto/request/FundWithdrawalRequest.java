package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FundWithdrawalRequest {

    @NotNull(message = "Branch id is required")
    private Long branchId;

    private LocalDate fromDate;

    private LocalDate toDate;

    // FundPeriodType enum value; defaults to CUSTOM when omitted.
    private String periodType;

    @NotNull(message = "System withdraw amount is required")
    @Positive(message = "System withdraw amount must be greater than zero")
    private Long systemWithdrawAmount;

    // Defaults to systemWithdrawAmount when omitted.
    private Long actualReceivedAmount;

    private String note;
}

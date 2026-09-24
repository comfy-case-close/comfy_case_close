package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarningDTO {

    public static final String WITHDRAW_EXCEEDS_REMAINING_POT = "WITHDRAW_EXCEEDS_REMAINING_POT";
    public static final String WITHDRAW_OVER_WARNING_THRESHOLD = "WITHDRAW_OVER_WARNING_THRESHOLD";
    public static final String PAYOUT_EXCEEDS_JAR_BALANCE = "PAYOUT_EXCEEDS_JAR_BALANCE";

    private String code;
    private long amount;
    private long limit;
}

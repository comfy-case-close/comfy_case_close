package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FundWithdrawalBranchDTO {

    private Long branchId;
    private String branchCode;
    private String branchName;
    private long generatedPot;
    private long withdrawn;
    private long actualReceived;
    private long variance;
    private long remainingPot;
}

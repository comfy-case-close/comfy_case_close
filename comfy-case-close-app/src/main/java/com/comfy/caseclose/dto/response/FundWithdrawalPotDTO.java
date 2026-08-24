package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** The withdrawal-pot dashboard: pot summary plus its source cash closes and posted withdrawals. */
@Data
@Builder
public class FundWithdrawalPotDTO {

    private Scope scope;
    private Summary summary;
    private List<FundWithdrawalBranchDTO> byBranch;
    private List<FundWithdrawalSourceDTO> sources;
    private List<FundWithdrawalResponseDTO> logs;

    @Data
    @Builder
    public static class Scope {
        private Long branchId;
        private String branchCode;
        private String branchLabel;
        private LocalDate fromDate;
        private LocalDate toDate;
        private String periodType;
    }

    @Data
    @Builder
    public static class Summary {
        private long generatedPot;
        private long alreadyWithdrawn;
        private long actualReceived;
        private long variance;
        private long remainingPot;
        private long sourceCount;
        private long logCount;
    }
}

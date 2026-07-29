package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** A cash close that contributed cash to the withdrawal pot for the period. */
@Data
@Builder
public class FundWithdrawalSourceDTO {

    private Long cashCloseId;
    private String referenceCode;
    private LocalDate businessDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String shiftName;
    private String submittedByName;
    private Long withdrawalAmount;
    private Long cashRemaining;
    private String note;
}

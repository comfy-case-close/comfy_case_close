package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One fund-withdrawal log row; variance and pot-after are computed, never stored. */
@Data
@Builder
public class FundWithdrawalResponseDTO {

    private Long id;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String periodType;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private Long createdById;
    private String createdByName;
    private OffsetDateTime createdAt;
    private Long systemPotBefore;
    private Long systemWithdrawAmount;
    private Long actualReceivedAmount;
    private Long varianceAmount;
    private Long systemPotAfter;
    private String note;
    private String status;
}

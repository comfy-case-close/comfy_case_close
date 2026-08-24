package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class BranchReportDTO {

    private Long branchId;
    private String branchCode;
    private String branchName;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long totalExpense;
    private long totalUnexplainedDiff;
    private long warningCount;
    private long pendingReviewCount;
    private double issueRate;
    private Long latestCashCloseId;
    private String latestReferenceCode;
    private LocalDate latestBusinessDate;
    private String latestShiftName;
    private long latestCashRemaining;
}

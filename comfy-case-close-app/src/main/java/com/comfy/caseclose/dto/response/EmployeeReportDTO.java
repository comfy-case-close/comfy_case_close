package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeReportDTO {

    private Long submittedById;
    private String submittedByCode;
    private String submittedByName;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long warningCount;
    private long pendingReviewCount;
    private long totalUnexplainedDiff;
    private long totalBillIssueAmount;
    private long totalCashIssueAmount;
    private long performanceScore;
    private String performanceLabel;
}

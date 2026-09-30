package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class EmployeeReportDTO {

    private UUID submittedById;
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

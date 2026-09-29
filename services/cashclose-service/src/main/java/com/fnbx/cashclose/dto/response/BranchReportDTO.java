package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class BranchReportDTO {

    private UUID branchId;
    private String branchCode;
    private String branchName;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long totalExpense;
    private long totalUnexplainedDiff;
    private long warningCount;
    private long pendingReviewCount;
    private double issueRate;
    /**
     * 0-100 percentage of closes currently sitting in PENDING_REVIEW, as of now — unlike
     * {@link #issueRate}, this drops a close the moment a manager approves or rejects it, rather
     * than counting a close forever once it was ever high-risk at submission. Computed the same
     * way as {@code KpiReportDTO.pendingRate}.
     */
    private double pendingRate;
    private UUID latestCashCloseId;
    private String latestReferenceCode;
    private LocalDate latestBusinessDate;
    private String latestShiftName;
    private long latestCashRemaining;
}

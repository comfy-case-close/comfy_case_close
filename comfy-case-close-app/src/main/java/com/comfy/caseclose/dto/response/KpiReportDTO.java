package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KpiReportDTO {

    private ReportScopeDTO scope;

    private long totalShiftClose;
    private long totalPosExpectedCash;
    private long totalCountedCash;
    private long totalCashDiffNet;
    private long totalCashDiffAbs;
    private long totalWithdrawal;
    private long totalExpense;
    private long totalTips;
    // totalTips split by flow: outside the drawer (tip jar) vs merged into the drawer.
    private long totalTipsSeparate;
    private long totalTipsInsideDrawer;
    private long totalUnexplainedDiff;
    private long totalBillIssueAmount;
    private long totalOperationalIssueAmount;
    private long totalOtherOperationalIssueAmount;
    private long totalCashIssueAmount;

    private long warningCount;
    private long pendingReviewCount;
    private long approvedCount;
    private long lateCount;
    private long morningCount;
    private long eveningCount;
    private long issueShiftCount;

    private long avgWithdrawalPerShift;
    private long avgExpensePerShift;
    private long avgTipsPerShift;
    private long avgTipsSeparatePerShift;
    private long avgTipsInsideDrawerPerShift;
    private long avgPosPerShift;
    private long avgCountedPerShift;

    private double issueRate;
    private double pendingRate;

    private long dataDays;
    private long branchCount;
}

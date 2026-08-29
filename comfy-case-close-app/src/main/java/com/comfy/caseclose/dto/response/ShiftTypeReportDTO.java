package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ShiftTypeReportDTO {

    private Long shiftTypeId;
    private String shiftTypeCode;
    private String shiftName;
    private LocalDate latestBusinessDate;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long totalExpense;
    private long warningCount;
    private long pendingReviewCount;
    private double issueRate;
    /** 0-100 percentage currently in PENDING_REVIEW — see {@code BranchReportDTO#pendingRate}. */
    private double pendingRate;
}

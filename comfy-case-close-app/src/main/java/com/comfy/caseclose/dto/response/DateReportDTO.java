package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DateReportDTO {

    private LocalDate businessDate;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long totalExpense;
    private long totalUnexplainedDiff;
    private long warningCount;
    private long pendingReviewCount;
}

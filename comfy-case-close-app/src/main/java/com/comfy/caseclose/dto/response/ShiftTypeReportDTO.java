package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShiftTypeReportDTO {

    private Long shiftTypeId;
    private String shiftTypeCode;
    private String shiftName;
    private long totalShiftClose;
    private long totalWithdrawal;
    private long totalExpense;
    private long warningCount;
    private double issueRate;
}

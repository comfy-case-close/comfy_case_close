package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class IssueReportDTO {

    private Long id;
    private String referenceCode;
    private LocalDate businessDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String shiftTypeCode;
    private String shiftName;
    private String submittedByName;
    private OffsetDateTime submittedAt;
    private long billIssueAmount;
    private long unexplainedDiff;
    private long totalCashIssueAmount;
    private long totalExpense;
    private long withdrawalAmount;
    private String status;
    private String riskLevel;
}

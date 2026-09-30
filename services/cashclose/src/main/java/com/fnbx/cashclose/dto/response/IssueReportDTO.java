package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class IssueReportDTO {

    private UUID id;
    private String referenceCode;
    private LocalDate businessDate;
    private UUID branchId;
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

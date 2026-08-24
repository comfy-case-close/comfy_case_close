package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReportScopeDTO {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Long branchId;
    private String branchLabel;
    private long totalDays;
}

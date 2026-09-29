package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ReportScopeDTO {

    private LocalDate fromDate;
    private LocalDate toDate;
    private UUID branchId;
    private String branchLabel;
    private long totalDays;
}

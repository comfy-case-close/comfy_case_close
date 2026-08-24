package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A single drill-down line item for a report category. */
@Data
@Builder
public class DetailItemDTO {

    private Long cashCloseId;
    private String referenceCode;
    private LocalDate businessDate;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String shiftTypeCode;
    private String shiftName;
    private String submittedByName;
    private OffsetDateTime submittedAt;
    private long amount;
    private String label;
    private String note;
}

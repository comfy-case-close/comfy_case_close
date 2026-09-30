package com.fnbx.cashclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A single drill-down line item for a report category. */
@Data
@Builder
public class DetailItemDTO {

    private UUID cashCloseId;
    private String referenceCode;
    private LocalDate businessDate;
    private UUID branchId;
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

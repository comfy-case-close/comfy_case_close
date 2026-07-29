package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftTypeResponseDTO {
    private Long id;
    private String shiftTypeCode;
    private String shiftTypeName;
    private Long startTime;  // time in minutes from midnight
    private Long endTime;  // time in minutes from midnight
    private Long branchId;
    private Boolean isActive;
}

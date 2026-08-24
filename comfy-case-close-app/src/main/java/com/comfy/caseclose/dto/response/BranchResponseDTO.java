package com.comfy.caseclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchResponseDTO {
    private Long id;
    private String branchCode;
    private String branchName;
    private Boolean isActive;
    private Long targetCashRemaining;  // nullable; NULL = inherit backend default
    private Long cashRemainingTolerance;  // nullable; NULL = inherit backend default
    private String managerEmail;
}

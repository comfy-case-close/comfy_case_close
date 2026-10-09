package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploymentAssignmentResponse {
    private UUID employmentAssignmentId;
    private UUID staffId;
    private UUID positionId;
    private UUID defaultBranchId;
    private String employmentType;
    private BigDecimal monthlyBaseSalary;
    private BigDecimal supplementAllowance;
    private BigDecimal fixedTotal;
    private BigDecimal hourlyBaseRate;
    private BigDecimal kpiAllowance;
    private BigDecimal responsibilityAllowance;
    private boolean insured;
    private boolean fixedSalary;
    private String jobLevel;
    private String contractKind;
    private String contractNo;
    private String probationResult;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String note;
}

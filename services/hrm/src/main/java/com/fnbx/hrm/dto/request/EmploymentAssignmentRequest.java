package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EmploymentType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmploymentAssignmentRequest {
    @NotNull
    private UUID positionId;
    @NotNull
    private UUID defaultBranchId;
    @NotNull
    private EmploymentType employmentType;
    private BigDecimal monthlyBaseSalary;
    private BigDecimal hourlyBaseRate;
    private BigDecimal kpiAllowance = BigDecimal.ZERO;
    private BigDecimal responsibilityAllowance = BigDecimal.ZERO;
    private boolean insured;
    private boolean fixedSalary;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String note;
}

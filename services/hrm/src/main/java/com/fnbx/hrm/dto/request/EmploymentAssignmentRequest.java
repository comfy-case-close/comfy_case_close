package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.JobLevel;
import com.fnbx.hrm.enums.ProbationResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full-time pay is sent in exactly one of two forms: {@code agreedMonthlySalary} with
 * {@code insuranceBase} (the server splits it), or {@code monthlyBaseSalary} with
 * {@code supplementAllowance}. Part-time pay is {@code hourlyBaseRate}.
 */
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
    @PositiveOrZero
    private BigDecimal agreedMonthlySalary;
    @PositiveOrZero
    private BigDecimal insuranceBase;
    @PositiveOrZero
    private BigDecimal monthlyBaseSalary;
    @PositiveOrZero
    private BigDecimal supplementAllowance;
    @PositiveOrZero
    private BigDecimal hourlyBaseRate;
    @PositiveOrZero
    private BigDecimal kpiAllowance = BigDecimal.ZERO;
    @PositiveOrZero
    private BigDecimal responsibilityAllowance = BigDecimal.ZERO;
    private boolean insured;
    private boolean fixedSalary;
    private JobLevel jobLevel;
    private ContractKind contractKind;
    private ProbationResult probationResult;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String note;
}

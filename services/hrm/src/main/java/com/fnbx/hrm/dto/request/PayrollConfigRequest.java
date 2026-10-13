package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.AppliesTo;
import com.fnbx.hrm.enums.PeriodMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST creates a new version with {@code effectiveFrom}; old versions stay immutable. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollConfigRequest {
    @NotNull
    private LocalDate effectiveFrom;
    @NotNull
    private BigDecimal standardDaysPerMonth;
    @NotNull
    private BigDecimal standardHoursPerDay;
    @NotNull
    private BigDecimal overtimeMultiplier;
    @NotNull
    private BigDecimal weekendMultiplier;
    private short payPeriodStartDay;
    @NotNull
    private PeriodMode periodMode;
    private String payslipEmailSubjectTemplate;
    private String payslipEmailBodyTemplate;
    /** One-off allowance paid in the month of a person's birthday; 0 means none. */
    @PositiveOrZero
    private BigDecimal birthdayAllowanceAmount = BigDecimal.ZERO;
    /** Who receives it: everyone, full-time only or part-time only. */
    private AppliesTo birthdayAllowanceAppliesTo = AppliesTo.BOTH;
}

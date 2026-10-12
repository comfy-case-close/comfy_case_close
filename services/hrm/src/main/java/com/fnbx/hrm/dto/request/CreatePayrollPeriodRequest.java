package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dates are optional and sent together; without them the period follows {@code payroll_config}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayrollPeriodRequest {
    @NotNull
    private Short periodYear;
    @NotNull
    @Min(1)
    @Max(12)
    private Short periodMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID copyRosterFromPeriodId;
}

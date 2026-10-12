package com.fnbx.hrm.dto.response;

import com.fnbx.hrm.enums.PeriodStatus;
import java.time.LocalDate;
import java.util.UUID;

/** The least an employee needs to know about a pay period they appear in: which month, which days, and whether it is final. */
public record OwnPeriodResponse(
        UUID payrollPeriodId,
        short periodYear,
        short periodMonth,
        LocalDate startDate,
        LocalDate endDate,
        PeriodStatus status) {
}

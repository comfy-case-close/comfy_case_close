package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.entity.EmployeeLeaveQuota;
import com.fnbx.hrm.repository.EmployeeLeaveQuotaRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Remaining annual leave = quota - (opening balance + distinct days coded CP this year).
 * Distinct days, not cells: one leave day booked on two payroll lines must cost one day.
 */
@Component
@RequiredArgsConstructor
public class AnnualLeaveBalanceCalculator {

    private static final String PAID_LEAVE_CODE = "CP";

    private final EmployeeLeaveQuotaRepository quotaRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;

    public AnnualLeaveBalanceResponse calculate(UUID staffId, short year) {
        EmployeeLeaveQuota quota = quotaRepository.findByStaffIdAndLeaveYear(staffId, year).orElse(null);
        BigDecimal quotaDays = quota == null ? BigDecimal.ZERO : quota.getQuotaDays();
        BigDecimal openingUsedDays = quota == null ? BigDecimal.ZERO : quota.getOpeningUsedDays();

        long paidLeaveDays = timesheetEntryRepository.countDistinctDatesByEmployeeAndCode(
                staffId, PAID_LEAVE_CODE, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        BigDecimal usedDays = openingUsedDays.add(BigDecimal.valueOf(paidLeaveDays));

        return AnnualLeaveBalanceResponse.builder()
                .leaveYear(year)
                .quotaDays(quotaDays)
                .openingUsedDays(openingUsedDays)
                .usedDays(usedDays)
                .remainingDays(quotaDays.subtract(usedDays))
                .build();
    }
}

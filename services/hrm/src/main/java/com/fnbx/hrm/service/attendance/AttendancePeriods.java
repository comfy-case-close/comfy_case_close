package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendancePeriods {

    private final PayrollPeriodRepository periodRepository;

    public Optional<PayrollPeriod> containing(LocalDate date) {
        return periodRepository.findOverlapping(date, date).stream().findFirst();
    }

    /** The period that receives the day; a period that is already locked or paid refuses it. */
    public PayrollPeriod requireOpenFor(LocalDate date) {
        PayrollPeriod period = containing(date)
                .orElseThrow(() -> PayrollExceptions.invalidField("No payroll period covers " + date + "; create it first"));
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.attendancePeriodClosed("The payroll period of " + date + " is " + period.getStatus());
        }
        return period;
    }
}

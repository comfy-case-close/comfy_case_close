package com.fnbx.hrm.service.period;

import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.enums.PeriodMode;
import com.fnbx.hrm.exception.PayrollExceptions;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class PeriodDateRangeResolver {

    private static final long MAX_PERIOD_DAYS = 62;

    public PeriodDateRange resolve(short year, short month, LocalDate startDate, LocalDate endDate, PayrollConfig config) {
        if (startDate == null && endDate == null) {
            return defaultRange(year, month, config);
        }
        if (startDate == null || endDate == null) {
            throw PayrollExceptions.invalidField("startDate and endDate must be sent together");
        }
        return validated(startDate, endDate);
    }

    public PeriodDateRange validated(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days < 1 || days > MAX_PERIOD_DAYS) {
            throw PayrollExceptions.periodDatesInvalid();
        }
        return new PeriodDateRange(startDate, endDate);
    }

    private PeriodDateRange defaultRange(short year, short month, PayrollConfig config) {
        YearMonth anchor = YearMonth.of(year, month);
        if (config.getPeriodMode() == PeriodMode.PREVIOUS_MONTH) {
            anchor = anchor.minusMonths(1);
        }
        LocalDate start = anchor.atDay(Math.min(config.getPayPeriodStartDay(), anchor.lengthOfMonth()));
        return new PeriodDateRange(start, start.plusMonths(1).minusDays(1));
    }
}

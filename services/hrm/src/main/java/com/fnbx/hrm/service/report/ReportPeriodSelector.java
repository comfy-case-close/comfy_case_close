package com.fnbx.hrm.service.report;

import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportPeriodSelector {

    private final PayrollPeriodRepository periodRepository;

    public List<UUID> select(UUID periodId, YearMonth from, YearMonth to) {
        if (from == null && to == null) {
            periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
            return List.of(periodId);
        }
        YearMonth first = from == null ? to : from;
        YearMonth last = to == null ? from : to;
        if (first.isAfter(last)) {
            throw PayrollExceptions.invalidField("fromMonth must not be after toMonth");
        }
        return periodRepository.findByMonthRange(key(first), key(last)).stream()
                .map(PayrollPeriod::getPayrollPeriodId).toList();
    }

    private int key(YearMonth month) {
        return month.getYear() * 100 + month.getMonthValue();
    }
}

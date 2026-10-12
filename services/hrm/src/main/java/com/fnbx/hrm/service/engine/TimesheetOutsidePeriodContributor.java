package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.TimesheetEntry;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimesheetOutsidePeriodContributor implements PeriodIssueContributor {

    private final TimesheetEntryRepository entryRepository;
    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        List<UUID> lineIds = lines.stream().map(PayrollLine::getPayrollLineId).toList();
        Map<UUID, Long> outsideByLine = entryRepository.findByPayrollLineIdIn(lineIds).stream()
                .filter(entry -> isOutside(entry, period))
                .collect(Collectors.groupingBy(TimesheetEntry::getPayrollLineId, Collectors.counting()));
        return outsideByLine.entrySet().stream()
                .map(outside -> issueFactory.warning(period, IssueCode.TIMESHEET_OUTSIDE_PERIOD,
                        "line:" + outside.getKey(),
                        outside.getValue() + " timesheet cells lie outside the period dates"))
                .toList();
    }

    private boolean isOutside(TimesheetEntry entry, PayrollPeriod period) {
        return entry.getWorkDate().isBefore(period.getStartDate()) || entry.getWorkDate().isAfter(period.getEndDate());
    }
}

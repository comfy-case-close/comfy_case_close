package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Paid hours on a day the line's contract did not cover (before it started or after it ended). */
@Component
@RequiredArgsConstructor
public class ContractInForceContributor implements PeriodIssueContributor {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final TimesheetEntryRepository entryRepository;
    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        List<DataValidationIssue> issues = new ArrayList<>();
        for (PayrollLine line : lines) {
            Optional<EmploymentAssignment> contract = assignmentRepository.findById(line.getAssignmentId());
            if (contract.isEmpty()) {
                continue;
            }
            LocalDate from = contract.get().getEffectiveFrom();
            LocalDate to = contract.get().getEffectiveTo();
            long outside = entryRepository.findByPayrollLineIdIn(List.of(line.getPayrollLineId())).stream()
                    .filter(cell -> cell.getPaidHours() != null && cell.getPaidHours().signum() > 0)
                    .filter(cell -> cell.getWorkDate().isBefore(from) || (to != null && cell.getWorkDate().isAfter(to)))
                    .count();
            if (outside > 0) {
                issues.add(issueFactory.warning(period, IssueCode.CONTRACT_NOT_IN_FORCE, "line:" + line.getPayrollLineId(),
                        outside + " day(s) with paid hours fall outside the contract's dates (" + from + " to "
                                + (to == null ? "open" : to.toString()) + ")"));
            }
        }
        return issues;
    }
}

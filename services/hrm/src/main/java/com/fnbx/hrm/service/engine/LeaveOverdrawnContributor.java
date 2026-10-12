package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.service.AnnualLeaveBalanceCalculator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Paid leave (CP) taken beyond the annual quota: allowed, but HR has to see it. */
@Component
@RequiredArgsConstructor
public class LeaveOverdrawnContributor implements PeriodIssueContributor {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final AnnualLeaveBalanceCalculator balanceCalculator;
    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        Set<UUID> staffIds = lines.stream()
                .map(line -> assignmentRepository.findById(line.getAssignmentId()))
                .flatMap(Optional::stream)
                .map(EmploymentAssignment::getStaffId)
                .collect(Collectors.toSet());
        short year = (short) period.getEndDate().getYear();
        return staffIds.stream()
                .filter(staffId -> balanceCalculator.calculate(staffId, year).getRemainingDays().signum() < 0)
                .map(staffId -> issueFactory.warning(period, IssueCode.LEAVE_OVERDRAWN, "staff:" + staffId,
                        "Paid leave taken in " + year + " is more than the annual quota"))
                .toList();
    }
}

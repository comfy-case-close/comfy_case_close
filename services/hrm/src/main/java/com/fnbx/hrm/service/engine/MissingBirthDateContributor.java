package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissingBirthDateContributor implements PeriodIssueContributor {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeProfileRepository profileRepository;
    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        if (config.getBirthdayAllowanceAmount().signum() == 0) {
            return List.of();
        }
        Set<UUID> staffIds = lines.stream()
                .map(line -> assignmentRepository.findById(line.getAssignmentId()))
                .flatMap(java.util.Optional::stream)
                .map(EmploymentAssignment::getStaffId)
                .collect(Collectors.toSet());
        return profileRepository.findAllById(staffIds).stream()
                .filter(profile -> profile.getDateOfBirth() == null)
                .map(profile -> issue(period, profile))
                .toList();
    }

    private DataValidationIssue issue(PayrollPeriod period, EmployeeProfile profile) {
        return issueFactory.warning(period, IssueCode.MISSING_BIRTH_DATE, "staff:" + profile.getStaffId(),
                "The employee has no date of birth, so the birthday allowance cannot be decided");
    }
}

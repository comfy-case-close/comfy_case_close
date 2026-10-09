package com.fnbx.hrm.service.employee;

import com.fnbx.hrm.config.HrmContractProperties;
import com.fnbx.hrm.dto.response.EmployeeListRow;
import com.fnbx.hrm.dto.response.EmployeeSummaryResponse;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeSummaryAssembler {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeProfileRepository profileRepository;
    private final ProfileCompletenessCalculator completenessCalculator;
    private final HrmContractProperties contractProperties;

    public List<EmployeeSummaryResponse> assemble(List<EmployeeListRow> rows) {
        LocalDate today = LocalDate.now();
        List<UUID> staffIds = rows.stream().map(EmployeeListRow::staffId).toList();
        Map<UUID, EmployeeProfile> profiles = profileRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(EmployeeProfile::getStaffId, Function.identity()));
        Map<UUID, EmploymentAssignment> contracts = currentContracts(staffIds, today);
        return rows.stream().map(row -> summarize(row, profiles.get(row.staffId()), contracts.get(row.staffId()), today)).toList();
    }

    private Map<UUID, EmploymentAssignment> currentContracts(List<UUID> staffIds, LocalDate today) {
        return assignmentRepository.findEffectiveForStaff(staffIds, today).stream()
                .collect(Collectors.toMap(EmploymentAssignment::getStaffId, Function.identity(),
                        (first, second) -> first.getEffectiveFrom().isBefore(second.getEffectiveFrom()) ? first : second));
    }

    private EmployeeSummaryResponse summarize(EmployeeListRow row, EmployeeProfile profile,
            EmploymentAssignment contract, LocalDate today) {
        LocalDate endsOn = contract == null ? null : contract.getEffectiveTo();
        boolean expiring = endsOn != null && !endsOn.isAfter(today.plusDays(contractProperties.expiryWarningDays()));
        boolean birthdayThisMonth = row.dateOfBirth() != null && row.dateOfBirth().getMonth() == today.getMonth();
        return new EmployeeSummaryResponse(row.staffId(), row.employeeCode(), row.nickname(), row.firstName(),
                row.lastName(), row.email(), row.dateOfBirth(), row.hiredOn(), row.terminatedOn(), row.active(),
                contract == null ? null : contract.getPositionId(),
                contract == null ? null : contract.getDefaultBranchId(),
                contract == null ? null : contract.getEmploymentType().name(),
                contract == null || contract.getJobLevel() == null ? null : contract.getJobLevel().name(),
                contract == null || contract.getContractKind() == null ? null : contract.getContractKind().name(),
                endsOn, expiring, birthdayThisMonth, completenessCalculator.calculate(profile).percent());
    }
}

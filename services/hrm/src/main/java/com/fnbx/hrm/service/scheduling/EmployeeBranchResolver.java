package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The branch and employment type an employee registers and works under in a given week, taken from their contract. */
@Component
@RequiredArgsConstructor
public class EmployeeBranchResolver {

    private final EmploymentAssignmentRepository assignmentRepository;

    public record EmployeeBranch(UUID branchId, EmploymentType employmentType) {}

    public EmployeeBranch resolve(UUID staffId, LocalDate weekStart) {
        return assignmentRepository.findEffectiveForStaff(List.of(staffId), weekStart).stream()
                .min(Comparator.comparing(EmploymentAssignment::getEffectiveFrom))
                .map(contract -> new EmployeeBranch(contract.getDefaultBranchId(), contract.getEmploymentType()))
                .orElseThrow(() -> PayrollExceptions.staffNotEligible("The employee has no contract in force for this week"));
    }
}

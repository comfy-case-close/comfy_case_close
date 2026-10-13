package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.BranchSetting;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.StaffSchedulingProfile;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.BranchSettingRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.StaffSchedulingProfileRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeBranchResolver {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final BranchSettingRepository branchSettingRepository;
    private final StaffSchedulingProfileRepository profileRepository;
    private final EntityManager entityManager;

    public record EmployeeBranch(UUID branchId, EmploymentType employmentType, List<UUID> branchIds) {

        public EmployeeBranch(UUID branchId, EmploymentType employmentType) {
            this(branchId, employmentType, List.of(branchId));
        }
    }

    public EmployeeBranch resolve(UUID staffId, LocalDate weekStart) {
        EmploymentAssignment contract = contractInForce(staffId, weekStart);
        EmploymentType type = contract.getEmploymentType();
        Optional<UUID> homeBranch = homeBranchOf(staffId);
        if (homeBranch.isPresent()) {
            return new EmployeeBranch(homeBranch.get(), type);
        }
        UUID defaultBranch = contract.getDefaultBranchId();
        Set<UUID> sharedPools = Set.copyOf(branchSettingRepository.findActiveSharedPoolBranchIds());
        if (!sharedPools.contains(defaultBranch)) {
            return new EmployeeBranch(defaultBranch, type);
        }
        List<UUID> sellingBranches = assignedSellingBranches(staffId, sharedPools);
        return sellingBranches.isEmpty()
                ? new EmployeeBranch(defaultBranch, type)
                : new EmployeeBranch(sellingBranches.get(0), type, sellingBranches);
    }

    private EmploymentAssignment contractInForce(UUID staffId, LocalDate weekStart) {
        return assignmentRepository.findEffectiveForStaff(List.of(staffId), weekStart).stream()
                .min(Comparator.comparing(EmploymentAssignment::getEffectiveFrom))
                .orElseThrow(() -> PayrollExceptions.staffNotEligible("The employee has no contract in force for this week"));
    }

    private Optional<UUID> homeBranchOf(UUID staffId) {
        return profileRepository.findById(staffId).map(StaffSchedulingProfile::getHomeBranchId);
    }

    private List<UUID> assignedSellingBranches(UUID staffId, Set<UUID> sharedPools) {
        List<UUID> branchIds = entityManager.createQuery("""
                SELECT DISTINCT a.branchId FROM StaffBranchPosition a
                WHERE a.staffId = :staffId AND a.revokedAt IS NULL AND a.assignedAt <= :now
                """, UUID.class)
                .setParameter("staffId", staffId).setParameter("now", Instant.now()).getResultList().stream()
                .filter(id -> !sharedPools.contains(id)).toList();
        return branchSettingRepository.findAllById(branchIds).stream()
                .filter(BranchSetting::isSellingStore)
                .sorted(Comparator.comparing(BranchSetting::getDisplayOrder))
                .map(BranchSetting::getBranchId).toList();
    }
}

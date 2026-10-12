package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.StaffSchedulingProfile;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.StaffSchedulingProfileRepository;
import com.fnbx.identity.entity.Staff;
import com.fnbx.identity.entity.StaffBranchPosition;
import com.fnbx.identity.entity.StaffPosition;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchedulingStaffLoader {

    private final EntityManager entityManager;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final StaffSchedulingProfileRepository profileRepository;

    public Map<UUID, SchedulingStaff> forBranch(UUID branchId, LocalDate weekStart) {
        return load(branchPositions(branchId), weekStart);
    }

    public Map<UUID, SchedulingStaff> forBusiness(UUID branchId, LocalDate weekStart) {
        Map<UUID, Set<UUID>> positionsByStaff = branchPositions(branchId);
        entityManager.createQuery("SELECT s.staffId FROM Staff s WHERE s.active = TRUE", UUID.class).getResultList()
                .forEach(staffId -> positionsByStaff.computeIfAbsent(staffId, id -> new HashSet<>()));
        return load(positionsByStaff, weekStart);
    }

    public Map<UUID, SchedulingStaff> byIds(Collection<UUID> staffIds, LocalDate weekStart) {
        Map<UUID, Set<UUID>> positionsByStaff = new HashMap<>();
        staffIds.forEach(staffId -> positionsByStaff.put(staffId, Set.of()));
        return load(positionsByStaff, weekStart, false);
    }

    public Map<UUID, String> positionNames(Collection<UUID> positionIds) {
        return positionIds.stream().distinct()
                .map(id -> entityManager.find(StaffPosition.class, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(StaffPosition::getPositionId, StaffPosition::getPositionName));
    }

    private Map<UUID, Set<UUID>> branchPositions(UUID branchId) {
        Map<UUID, Set<UUID>> positionsByStaff = new HashMap<>();
        entityManager.createQuery("""
                SELECT a FROM StaffBranchPosition a
                WHERE a.branchId = :branchId AND a.revokedAt IS NULL AND a.assignedAt <= :now
                """, StaffBranchPosition.class)
                .setParameter("branchId", branchId).setParameter("now", Instant.now()).getResultList()
                .forEach(position -> positionsByStaff
                        .computeIfAbsent(position.getStaffId(), id -> new HashSet<>()).add(position.getPositionId()));
        return positionsByStaff;
    }

    private Map<UUID, SchedulingStaff> load(Map<UUID, Set<UUID>> positionsByStaff, LocalDate weekStart) {
        return load(positionsByStaff, weekStart, true);
    }

    private Map<UUID, SchedulingStaff> load(Map<UUID, Set<UUID>> positionsByStaff, LocalDate weekStart, boolean activeOnly) {
        if (positionsByStaff.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<EmploymentAssignment>> contracts = assignmentRepository
                .findOverlapping(weekStart, WeekCalendar.lastDay(weekStart)).stream()
                .filter(contract -> positionsByStaff.containsKey(contract.getStaffId()))
                .collect(Collectors.groupingBy(EmploymentAssignment::getStaffId));
        Map<UUID, UUID> homeBranches = profileRepository.findAllById(positionsByStaff.keySet()).stream()
                .filter(profile -> profile.getHomeBranchId() != null)
                .collect(Collectors.toMap(StaffSchedulingProfile::getStaffId, StaffSchedulingProfile::getHomeBranchId));
        Set<UUID> managers = managersAmong(positionsByStaff.keySet());
        return entityManager.createQuery(activeOnly ? "SELECT s FROM Staff s WHERE s.staffId IN :ids AND s.active = TRUE"
                        : "SELECT s FROM Staff s WHERE s.staffId IN :ids", Staff.class)
                .setParameter("ids", positionsByStaff.keySet()).getResultStream()
                .collect(Collectors.toMap(Staff::getStaffId, person -> new SchedulingStaff(person.getStaffId(),
                        person.getNickname(), person.getFirstName(), person.getFirstName() + " " + person.getLastName(),
                        positionsByStaff.get(person.getStaffId()), contracts.getOrDefault(person.getStaffId(), List.of()),
                        homeBranches.get(person.getStaffId()), managers.contains(person.getStaffId()))));
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> managersAmong(Collection<UUID> staffIds) {
        if (staffIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> found = entityManager.createNativeQuery("""
                SELECT DISTINCT sbp.staff_id FROM identity.staff_branch_position sbp
                JOIN identity.position_permission pp ON pp.position_id = sbp.position_id AND pp.revoked_at IS NULL
                WHERE sbp.revoked_at IS NULL AND sbp.staff_id IN (:ids)
                  AND pp.permission_code IN ('SHIFT_SCHEDULE_EDIT', 'SHIFT_SCHEDULE_APPROVE')
                """).setParameter("ids", staffIds).getResultList();
        return Set.copyOf(found);
    }
}

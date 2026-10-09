package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.EmploymentType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record SchedulingStaff(UUID staffId, String nickname, String firstName, String fullName,
                              Set<UUID> positionIds, List<EmploymentAssignment> contracts) {

    public String displayName() {
        return nickname == null || nickname.isBlank() ? firstName : nickname;
    }

    public boolean canFill(UUID positionId) {
        return positionIds.contains(positionId);
    }

    public Optional<EmploymentType> typeOn(LocalDate date) {
        return contracts.stream()
                .filter(contract -> !contract.getEffectiveFrom().isAfter(date)
                        && (contract.getEffectiveTo() == null || !contract.getEffectiveTo().isBefore(date)))
                .min(Comparator.comparing(EmploymentAssignment::getEffectiveFrom))
                .map(EmploymentAssignment::getEmploymentType);
    }
}

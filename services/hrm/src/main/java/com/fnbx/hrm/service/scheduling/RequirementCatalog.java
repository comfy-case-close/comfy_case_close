package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftPeriodRequirement;
import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.repository.ShiftPeriodRequirementRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Minimum people per position that must be working at every moment of a shift period. */
@Component
@RequiredArgsConstructor
public class RequirementCatalog {

    private final ShiftPeriodRequirementRepository requirementRepository;

    public Map<ShiftPeriod, Map<UUID, Integer>> inForce(UUID branchId, LocalDate date) {
        Map<ShiftPeriod, Map<UUID, Integer>> requirements = new EnumMap<>(ShiftPeriod.class);
        for (ShiftPeriodRequirement row : requirementRepository.findInForce(branchId, date)) {
            requirements.computeIfAbsent(row.getShiftPeriod(), period -> new HashMap<>())
                    .putIfAbsent(row.getPositionId(), (int) row.getMinStaff());
        }
        return requirements;
    }
}

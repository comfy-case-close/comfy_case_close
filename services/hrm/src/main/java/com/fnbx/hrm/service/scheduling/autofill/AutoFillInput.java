package com.fnbx.hrm.service.scheduling.autofill;

import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.service.scheduling.AvailabilityIndex;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.WeekSlots;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the auto-fill needs, already loaded, so the algorithm itself touches no repository.
 *
 * @param otherBranchDays days on which a person already works at another branch and so cannot be used here
 * @param lastWeek        who worked which slot on which weekday in the previous week of this branch
 * @param minMinutes      weekly minimum per person, in minutes
 */
public record AutoFillInput(
        LocalDate weekStart,
        WeekSlots slots,
        Map<LocalDate, Map<ShiftPeriod, Map<UUID, Integer>>> requirements,
        Map<UUID, SchedulingStaff> staff,
        AvailabilityIndex availability,
        List<Placed> existing,
        Map<UUID, Set<LocalDate>> otherBranchDays,
        Set<LastWeekKey> lastWeek,
        Map<UUID, Integer> minMinutes,
        boolean keepLastWeek) {

    public record LastWeekKey(UUID staffId, int dayIndex, String slotName) {}
}

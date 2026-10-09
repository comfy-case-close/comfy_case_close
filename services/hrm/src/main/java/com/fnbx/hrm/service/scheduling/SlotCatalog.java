package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlotCatalog {

    private final ShiftSlotRepository slotRepository;

    public WeekSlots forWeek(UUID branchId, LocalDate weekStart) {
        return forWeek(branchId, weekStart, List.of());
    }

    /** {@code referencedSlotIds} adds slots outside their effective range, e.g. one an old assignment still uses. */
    public WeekSlots forWeek(UUID branchId, LocalDate weekStart, Collection<UUID> referencedSlotIds) {
        Map<LocalDate, List<ShiftSlot>> byDate = new LinkedHashMap<>();
        for (LocalDate date : WeekCalendar.days(weekStart)) {
            byDate.put(date, slotRepository.findEffective(branchId, date, WeekCalendar.dayTypeOf(date)));
        }
        return new WeekSlots(byDate, slotRepository.findAllById(referencedSlotIds));
    }
}

package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.exception.PayrollExceptions;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The shift slots in force on each day of one branch week, plus any older slot an assignment still points at. */
public final class WeekSlots {

    private final Map<LocalDate, List<ShiftSlot>> byDate;
    private final Map<UUID, ShiftSlot> byId;

    WeekSlots(Map<LocalDate, List<ShiftSlot>> byDate, List<ShiftSlot> extra) {
        this.byDate = byDate;
        this.byId = java.util.stream.Stream.concat(byDate.values().stream().flatMap(List::stream), extra.stream())
                .collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity(), (first, second) -> first));
    }

    public List<ShiftSlot> on(LocalDate date) {
        return byDate.getOrDefault(date, List.of());
    }

    public ShiftSlot byId(UUID shiftSlotId) {
        ShiftSlot slot = byId.get(shiftSlotId);
        if (slot == null) {
            throw PayrollExceptions.shiftSlotNotFound();
        }
        return slot;
    }

    public boolean contains(UUID shiftSlotId) {
        return byId.containsKey(shiftSlotId);
    }
}

package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.service.scheduling.SegmentCoverage.PositionNeed;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Splits each shift period into time windows at every slot boundary and counts, per position,
 * who is working through the whole window. A full-time assignment spans every window of its shift.
 */
@Component
@RequiredArgsConstructor
public class CoverageCalculator {

    private final RequirementCatalog requirementCatalog;

    public List<SegmentCoverage> compute(UUID branchId, LocalDate weekStart, WeekSlots slots, List<ShiftAssignment> assignments) {
        List<SegmentCoverage> coverage = new ArrayList<>();
        for (LocalDate date : WeekCalendar.days(weekStart)) {
            Map<ShiftPeriod, Map<UUID, Integer>> requirements = requirementCatalog.inForce(branchId, date);
            for (ShiftPeriod period : ShiftPeriod.values()) {
                Map<UUID, Integer> required = requirements.getOrDefault(period, Map.of());
                if (required.isEmpty()) {
                    continue;
                }
                for (TimeWindow window : segments(slots.on(date), period)) {
                    coverage.add(new SegmentCoverage(date, period, window, needs(required, window, date, period, slots, assignments)));
                }
            }
        }
        return coverage;
    }

    public List<TimeWindow> segments(List<ShiftSlot> daySlots, ShiftPeriod period) {
        List<ShiftSlot> periodSlots = daySlots.stream().filter(slot -> slot.getShiftPeriod() == period).toList();
        TreeSet<LocalTime> boundaries = new TreeSet<>();
        periodSlots.forEach(slot -> {
            boundaries.add(slot.getStartTime());
            boundaries.add(slot.getEndTime());
        });
        List<LocalTime> ordered = List.copyOf(boundaries);
        List<TimeWindow> windows = new ArrayList<>();
        for (int index = 0; index + 1 < ordered.size(); index++) {
            TimeWindow window = new TimeWindow(ordered.get(index), ordered.get(index + 1));
            if (periodSlots.stream().anyMatch(slot -> TimeWindow.of(slot).covers(window))) {
                windows.add(window);
            }
        }
        return windows;
    }

    private List<PositionNeed> needs(Map<UUID, Integer> required, TimeWindow window, LocalDate date, ShiftPeriod period,
            WeekSlots slots, List<ShiftAssignment> assignments) {
        return required.entrySet().stream()
                .map(need -> new PositionNeed(need.getKey(), need.getValue(),
                        present(need.getKey(), window, date, period, slots, assignments)))
                .toList();
    }

    private int present(UUID positionId, TimeWindow window, LocalDate date, ShiftPeriod period, WeekSlots slots,
            List<ShiftAssignment> assignments) {
        return (int) assignments.stream()
                .filter(assignment -> assignment.getWorkDate().equals(date) && assignment.getPositionId().equals(positionId))
                .filter(assignment -> slots.byId(assignment.getShiftSlotId()).getShiftPeriod() == period)
                .filter(assignment -> TimeWindow.of(assignment).covers(window))
                .map(ShiftAssignment::getStaffId)
                .distinct()
                .count();
    }
}

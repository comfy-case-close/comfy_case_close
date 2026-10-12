package com.fnbx.hrm.service.scheduling.autofill;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.service.scheduling.RequirementCatalog;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.SchedulingLimitsProvider;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.hrm.service.scheduling.WeekSlots;
import com.fnbx.hrm.service.scheduling.autofill.AutoFillInput.LastWeekKey;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoFillInputLoader {

    private static final int DAYS_IN_WEEK = 7;

    private final RequirementCatalog requirementCatalog;
    private final SchedulingLimitsProvider limitsProvider;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftScheduleRepository scheduleRepository;
    private final ShiftSlotRepository slotRepository;

    public AutoFillInput load(ScheduleContext context, boolean keepLastWeek) {
        ShiftSchedule schedule = context.schedule();
        LocalDate weekStart = schedule.getWeekStart();
        return new AutoFillInput(weekStart, context.slots(), requirements(schedule), context.staff(), context.availability(),
                existing(context), otherBranchDays(context), keepLastWeek ? lastWeek(schedule) : Set.of(),
                limitsProvider.minMinutes(context.limits(), context.staff(), weekStart), keepLastWeek);
    }

    private Map<LocalDate, Map<ShiftPeriod, Map<UUID, Integer>>> requirements(ShiftSchedule schedule) {
        Map<LocalDate, Map<ShiftPeriod, Map<UUID, Integer>>> byDate = new HashMap<>();
        WeekCalendar.days(schedule.getWeekStart())
                .forEach(date -> byDate.put(date, requirementCatalog.inForce(schedule.getBranchId(), date)));
        return byDate;
    }

    private List<Placed> existing(ScheduleContext context) {
        return context.assignments().stream().map(assignment -> {
            ShiftSlot slot = context.slots().byId(assignment.getShiftSlotId());
            return new Placed(assignment.getStaffId(), assignment.getWorkDate(), slot.getShiftSlotId(), slot.getName(),
                    slot.getShiftPeriod(), assignment.getPositionId(), TimeWindow.of(assignment));
        }).toList();
    }

    private Map<UUID, Set<LocalDate>> otherBranchDays(ScheduleContext context) {
        Map<UUID, Set<LocalDate>> days = new HashMap<>();
        assignmentRepository.findByStaffIdInAndWorkDateBetween(context.staff().keySet(), context.schedule().getWeekStart(),
                        WeekCalendar.lastDay(context.schedule().getWeekStart())).stream()
                .filter(assignment -> !assignment.getBranchId().equals(context.schedule().getBranchId()))
                .forEach(assignment -> days.computeIfAbsent(assignment.getStaffId(), id -> new HashSet<>()).add(assignment.getWorkDate()));
        return days;
    }

    private Set<LastWeekKey> lastWeek(ShiftSchedule schedule) {
        LocalDate previousWeek = schedule.getWeekStart().minusDays(DAYS_IN_WEEK);
        return scheduleRepository.findByBranchIdAndWeekStart(schedule.getBranchId(), previousWeek)
                .map(previous -> lastWeekKeys(previous, previousWeek)).orElse(Set.of());
    }

    private Set<LastWeekKey> lastWeekKeys(ShiftSchedule previous, LocalDate previousWeek) {
        List<ShiftAssignment> assignments = assignmentRepository.findByShiftScheduleId(previous.getShiftScheduleId());
        Map<UUID, ShiftSlot> slots = slotRepository.findAllById(assignments.stream().map(ShiftAssignment::getShiftSlotId).toList()).stream()
                .collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
        return assignments.stream().map(assignment -> new LastWeekKey(assignment.getStaffId(),
                (int) java.time.temporal.ChronoUnit.DAYS.between(previousWeek, assignment.getWorkDate()),
                slots.get(assignment.getShiftSlotId()).getName())).collect(Collectors.toSet());
    }
}

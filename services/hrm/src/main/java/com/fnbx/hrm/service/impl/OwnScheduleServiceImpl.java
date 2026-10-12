package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.OwnScheduleResponses.DayView;
import com.fnbx.hrm.dto.response.OwnScheduleResponses.Employment;
import com.fnbx.hrm.dto.response.OwnScheduleResponses.MyShift;
import com.fnbx.hrm.dto.response.OwnScheduleResponses.Person;
import com.fnbx.hrm.dto.response.OwnScheduleResponses.Week;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.service.OwnScheduleService;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.SchedulingStaffLoader;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.identity.entity.Branch;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnScheduleServiceImpl implements OwnScheduleService {

    private static final Set<ScheduleStatus> VISIBLE = Set.of(ScheduleStatus.PUBLISHED, ScheduleStatus.LOCKED);

    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftScheduleRepository scheduleRepository;
    private final ShiftSlotRepository slotRepository;
    private final SchedulingStaffLoader staffLoader;
    private final EmployeeBranchResolver branchResolver;
    private final EmploymentAssignmentRepository employmentRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Employment employment() {
        LocalDate today = LocalDate.now();
        boolean employed = employmentRepository.findByStaffIdOrderByEffectiveFromDesc(TenantContext.current().userId()).stream()
                .map(EmploymentAssignment::getEffectiveTo)
                .anyMatch(end -> end == null || !end.isBefore(today));
        return new Employment(employed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyShift> myShifts(LocalDate from, LocalDate to) {
        UUID staffId = TenantContext.current().userId();
        List<ShiftAssignment> mine = assignmentRepository.findByStaffIdAndWorkDateBetween(staffId, from, to).stream()
                .filter(assignment -> isVisible(assignment.getShiftScheduleId())).toList();
        return toShifts(mine);
    }

    @Override
    @Transactional(readOnly = true)
    public Week week(LocalDate weekStart) {
        UUID staffId = TenantContext.current().userId();
        WeekCalendar.requireMonday(weekStart);
        UUID branchId = assignmentRepository.findByStaffIdAndWorkDateBetween(staffId, weekStart, WeekCalendar.lastDay(weekStart)).stream()
                .findFirst().map(ShiftAssignment::getBranchId).orElseGet(() -> branchResolver.resolve(staffId, weekStart).branchId());
        String branchName = entityManager.find(Branch.class, branchId).getBranchName();
        ShiftSchedule schedule = scheduleRepository.findByBranchIdAndWeekStart(branchId, weekStart).filter(found -> VISIBLE.contains(found.getStatus())).orElse(null);
        if (schedule == null) {
            return new Week(weekStart, branchId, branchName, false, List.of(), List.of());
        }
        List<ShiftAssignment> all = assignmentRepository.findByShiftScheduleId(schedule.getShiftScheduleId());
        return new Week(weekStart, branchId, branchName, true, days(weekStart, all, staffId),
                toShifts(all.stream().filter(assignment -> assignment.getStaffId().equals(staffId)).toList()));
    }

    private List<DayView> days(LocalDate weekStart, List<ShiftAssignment> assignments, UUID me) {
        Map<UUID, ShiftSlot> slots = slotsOf(assignments);
        Map<UUID, SchedulingStaff> staff = staffLoader.byIds(assignments.stream().map(ShiftAssignment::getStaffId).toList(), weekStart);
        Map<UUID, String> positions = staffLoader.positionNames(assignments.stream().map(ShiftAssignment::getPositionId).toList());
        return WeekCalendar.days(weekStart).stream().map(date -> new DayView(date,
                people(assignments, slots, staff, positions, me, date, ShiftPeriod.MORNING),
                people(assignments, slots, staff, positions, me, date, ShiftPeriod.EVENING))).toList();
    }

    private List<Person> people(List<ShiftAssignment> assignments, Map<UUID, ShiftSlot> slots, Map<UUID, SchedulingStaff> staff,
            Map<UUID, String> positions, UUID me, LocalDate date, ShiftPeriod period) {
        return assignments.stream()
                .filter(assignment -> assignment.getWorkDate().equals(date) && slots.get(assignment.getShiftSlotId()).getShiftPeriod() == period)
                .map(assignment -> new Person(assignment.getStaffId(), nameOf(staff.get(assignment.getStaffId())),
                        positions.get(assignment.getPositionId()), assignment.getStaffId().equals(me)))
                .distinct()
                .sorted(Comparator.comparing(Person::positionName, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Person::nickname))
                .toList();
    }

    private List<MyShift> toShifts(List<ShiftAssignment> assignments) {
        Map<UUID, ShiftSlot> slots = slotsOf(assignments);
        Map<UUID, String> positions = staffLoader.positionNames(assignments.stream().map(ShiftAssignment::getPositionId).toList());
        return assignments.stream().sorted(Comparator.comparing(ShiftAssignment::getWorkDate).thenComparing(ShiftAssignment::getStartTime))
                .map(assignment -> new MyShift(assignment.getShiftAssignmentId(), assignment.getWorkDate(), assignment.getBranchId(),
                        entityManager.find(Branch.class, assignment.getBranchId()).getBranchName(),
                        slots.get(assignment.getShiftSlotId()).getName(), assignment.getStartTime(), assignment.getEndTime(),
                        positions.get(assignment.getPositionId())))
                .toList();
    }

    private Map<UUID, ShiftSlot> slotsOf(List<ShiftAssignment> assignments) {
        return slotRepository.findAllById(assignments.stream().map(ShiftAssignment::getShiftSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
    }

    private String nameOf(SchedulingStaff person) {
        return person == null ? "?" : person.displayName();
    }

    private boolean isVisible(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).map(schedule -> VISIBLE.contains(schedule.getStatus())).orElse(false);
    }
}

package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.dto.response.AvailabilityResponses.DayForm;
import com.fnbx.hrm.dto.response.AvailabilityResponses.OwnForm;
import com.fnbx.hrm.dto.response.AvailabilityResponses.SlotChoice;
import com.fnbx.hrm.entity.AvailabilitySubmission;
import com.fnbx.hrm.entity.BusyShift;
import com.fnbx.hrm.enums.RegistrationStatus;
import com.fnbx.hrm.enums.SubmissionStatus;
import com.fnbx.hrm.repository.AvailabilitySubmissionRepository;
import com.fnbx.hrm.repository.BusyShiftRepository;
import com.fnbx.hrm.repository.RegistrationWindowRepository;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver.EmployeeBranch;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityFormAssembler {

    private final SlotCatalog slotCatalog;
    private final RegistrationWindowRepository windowRepository;
    private final AvailabilitySubmissionRepository submissionRepository;
    private final BusyShiftRepository busyShiftRepository;

    public OwnForm assemble(UUID staffId, LocalDate weekStart, EmployeeBranch employee) {
        AvailabilitySubmission submission = submissionRepository.findByStaffIdAndWeekStart(staffId, weekStart).orElse(null);
        Set<String> busyKeys = busyKeys(submission);
        WeekSlots slots = formSlots(employee, weekStart);
        List<DayForm> days = WeekCalendar.days(weekStart).stream()
                .map(date -> dayForm(date, slots, employee, busyKeys))
                .toList();
        return new OwnForm(weekStart, employee.branchId(), isOpen(employee, weekStart),
                submission == null ? SubmissionStatus.DRAFT.name() : submission.getStatus().name(),
                submission == null ? null : submission.getNote(), submission == null ? null : submission.getSubmittedAt(),
                submission == null ? 0 : submission.getVersion(), days);
    }

    private Set<String> busyKeys(AvailabilitySubmission submission) {
        if (submission == null) {
            return Set.of();
        }
        return busyShiftRepository.findByAvailabilitySubmissionId(submission.getAvailabilitySubmissionId()).stream()
                .map(this::key).collect(Collectors.toSet());
    }

    private DayForm dayForm(LocalDate date, WeekSlots slots, EmployeeBranch employee, Set<String> busyKeys) {
        List<SlotChoice> choices = slots.on(date).stream()
                .filter(slot -> slot.getEmploymentType() == employee.employmentType())
                .map(slot -> new SlotChoice(slot.getShiftSlotId(), slot.getName(), slot.getStartTime(), slot.getEndTime(),
                        busyKeys.contains(key(date, slot.getShiftSlotId()))))
                .toList();
        return new DayForm(date, choices);
    }

    public WeekSlots formSlots(EmployeeBranch employee, LocalDate weekStart) {
        List<WeekSlots> perBranch = employee.branchIds().stream().map(branchId -> slotCatalog.forWeek(branchId, weekStart)).toList();
        return perBranch.stream().filter(slots -> hasAnySlot(slots, weekStart)).findFirst().orElse(perBranch.get(0));
    }

    private boolean hasAnySlot(WeekSlots slots, LocalDate weekStart) {
        return WeekCalendar.days(weekStart).stream().anyMatch(date -> !slots.on(date).isEmpty());
    }

    public boolean isOpen(EmployeeBranch employee, LocalDate weekStart) {
        return employee.branchIds().stream().anyMatch(branchId -> isOpen(branchId, weekStart));
    }

    public boolean isOpen(UUID branchId, LocalDate weekStart) {
        return windowRepository.findByBranchIdAndWeekStart(branchId, weekStart)
                .map(window -> window.getStatus() == RegistrationStatus.OPEN).orElse(false);
    }

    private String key(BusyShift shift) {
        return key(shift.getWorkDate(), shift.getShiftSlotId());
    }

    private String key(LocalDate date, UUID shiftSlotId) {
        return date + "|" + shiftSlotId;
    }
}

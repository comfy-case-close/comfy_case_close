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
        Set<String> busy = submission == null ? Set.of()
                : busyShiftRepository.findByAvailabilitySubmissionId(submission.getAvailabilitySubmissionId()).stream()
                        .map(this::key).collect(Collectors.toSet());
        WeekSlots slots = slotCatalog.forWeek(employee.branchId(), weekStart);
        List<DayForm> days = WeekCalendar.days(weekStart).stream()
                .map(date -> new DayForm(date, slots.on(date).stream()
                        .filter(slot -> slot.getEmploymentType() == employee.employmentType())
                        .map(slot -> new SlotChoice(slot.getShiftSlotId(), slot.getName(), slot.getStartTime(), slot.getEndTime(),
                                busy.contains(key(date, slot.getShiftSlotId()))))
                        .toList()))
                .toList();
        return new OwnForm(weekStart, employee.branchId(), isOpen(employee.branchId(), weekStart),
                submission == null ? SubmissionStatus.DRAFT.name() : submission.getStatus().name(),
                submission == null ? null : submission.getNote(), submission == null ? null : submission.getSubmittedAt(),
                submission == null ? 0 : submission.getVersion(), days);
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

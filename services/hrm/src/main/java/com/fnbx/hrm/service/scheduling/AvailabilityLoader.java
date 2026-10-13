package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.AvailabilitySubmission;
import com.fnbx.hrm.entity.BusyShift;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.SubmissionStatus;
import com.fnbx.hrm.repository.AvailabilitySubmissionRepository;
import com.fnbx.hrm.repository.BusyShiftRepository;
import com.fnbx.hrm.repository.ShiftSlotRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.service.scheduling.AvailabilityIndex.BusyWindow;
import com.fnbx.hrm.service.scheduling.AvailabilityIndex.WeekReport;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityLoader {

    private final AvailabilitySubmissionRepository submissionRepository;
    private final BusyShiftRepository busyShiftRepository;
    private final ShiftSlotRepository slotRepository;
    private final TimesheetEntryRepository timesheetRepository;

    public AvailabilityIndex load(LocalDate weekStart, Collection<UUID> staffIds, java.util.Set<UUID> optional) {
        List<AvailabilitySubmission> submissions = submissionRepository.findByWeekStartAndStaffIdIn(weekStart, staffIds);
        Map<UUID, List<BusyShift>> busyBySubmission = busyShiftRepository
                .findByAvailabilitySubmissionIdIn(submissions.stream().map(AvailabilitySubmission::getAvailabilitySubmissionId).toList())
                .stream().collect(Collectors.groupingBy(BusyShift::getAvailabilitySubmissionId));
        Map<UUID, ShiftSlot> slots = slotRepository.findAllById(busyBySubmission.values().stream()
                        .flatMap(List::stream).map(BusyShift::getShiftSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(ShiftSlot::getShiftSlotId, Function.identity()));
        Map<UUID, WeekReport> reports = new HashMap<>();
        for (AvailabilitySubmission submission : submissions) {
            List<BusyWindow> busy = busyBySubmission.getOrDefault(submission.getAvailabilitySubmissionId(), List.of()).stream()
                    .map(shift -> new BusyWindow(shift.getWorkDate(), TimeWindow.of(slots.get(shift.getShiftSlotId()))))
                    .toList();
            reports.put(submission.getStaffId(), new WeekReport(submission.getStatus() == SubmissionStatus.SUBMITTED, busy));
        }
        Map<UUID, java.util.Set<LocalDate>> leave = new HashMap<>();
        if (!staffIds.isEmpty()) {
            timesheetRepository.leaveDays(staffIds, weekStart, WeekCalendar.lastDay(weekStart))
                    .forEach(row -> leave.computeIfAbsent((UUID) row[0], id -> new java.util.HashSet<>()).add((LocalDate) row[1]));
        }
        return new AvailabilityIndex(reports, optional, leave);
    }
}

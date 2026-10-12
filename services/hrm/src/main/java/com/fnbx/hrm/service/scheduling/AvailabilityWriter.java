package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.dto.request.SaveAvailabilityRequest;
import com.fnbx.hrm.dto.request.SaveAvailabilityRequest.BusyShiftInput;
import com.fnbx.hrm.entity.AvailabilitySubmission;
import com.fnbx.hrm.entity.BusyShift;
import com.fnbx.hrm.enums.SubmissionStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AvailabilitySubmissionRepository;
import com.fnbx.hrm.repository.BusyShiftRepository;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver.EmployeeBranch;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityWriter {

    private final AvailabilitySubmissionRepository submissionRepository;
    private final BusyShiftRepository busyShiftRepository;
    private final SlotCatalog slotCatalog;

    public AvailabilitySubmission save(UUID staffId, LocalDate weekStart, EmployeeBranch employee,
            SaveAvailabilityRequest request, SubmissionStatus status, UUID enteredBy) {
        AvailabilitySubmission submission = submissionRepository.findByStaffIdAndWeekStart(staffId, weekStart)
                .orElseGet(() -> newSubmission(staffId, weekStart));
        if (request.expectedVersion() != null && submission.getVersion() != request.expectedVersion()) {
            throw PayrollExceptions.versionConflict();
        }
        requireOffered(employee, weekStart, request.busyShifts());
        submission.setNote(request.note());
        submission.setEnteredBy(enteredBy);
        applyStatus(submission, status);
        submission.setVersion(submission.getVersion() + 1);
        submissionRepository.saveAndFlush(submission);
        replaceBusyShifts(submission, request.busyShifts());
        return submission;
    }

    public AvailabilitySubmission submit(UUID staffId, LocalDate weekStart) {
        AvailabilitySubmission submission = submissionRepository.findByStaffIdAndWeekStart(staffId, weekStart)
                .orElseGet(() -> newSubmission(staffId, weekStart));
        applyStatus(submission, SubmissionStatus.SUBMITTED);
        submission.setVersion(submission.getVersion() + 1);
        return submissionRepository.saveAndFlush(submission);
    }

    public Optional<List<BusyShift>> busyOf(UUID staffId, LocalDate weekStart) {
        return submissionRepository.findByStaffIdAndWeekStart(staffId, weekStart)
                .map(submission -> busyShiftRepository.findByAvailabilitySubmissionId(submission.getAvailabilitySubmissionId()));
    }

    private void requireOffered(EmployeeBranch employee, LocalDate weekStart, List<BusyShiftInput> busyShifts) {
        WeekSlots slots = slotCatalog.forWeek(employee.branchId(), weekStart);
        for (BusyShiftInput input : busyShifts) {
            boolean offered = slots.on(input.date()).stream().anyMatch(slot -> slot.getShiftSlotId().equals(input.shiftSlotId())
                    && slot.getEmploymentType() == employee.employmentType());
            if (!offered) {
                throw PayrollExceptions.invalidField("Shift " + input.shiftSlotId() + " is not offered on " + input.date());
            }
        }
    }

    private void replaceBusyShifts(AvailabilitySubmission submission, List<BusyShiftInput> busyShifts) {
        busyShiftRepository.deleteBySubmission(submission.getAvailabilitySubmissionId());
        busyShiftRepository.flush();
        List<BusyShift> rows = busyShifts.stream().distinct().map(input -> {
            BusyShift row = new BusyShift();
            row.setAvailabilitySubmissionId(submission.getAvailabilitySubmissionId());
            row.setBusinessId(submission.getBusinessId());
            row.setWorkDate(input.date());
            row.setShiftSlotId(input.shiftSlotId());
            return row;
        }).toList();
        busyShiftRepository.saveAll(rows);
    }

    private void applyStatus(AvailabilitySubmission submission, SubmissionStatus status) {
        submission.setStatus(status);
        submission.setSubmittedAt(status == SubmissionStatus.SUBMITTED ? Instant.now() : null);
    }

    private AvailabilitySubmission newSubmission(UUID staffId, LocalDate weekStart) {
        AvailabilitySubmission submission = new AvailabilitySubmission();
        submission.setAvailabilitySubmissionId(UUID.randomUUID());
        submission.setBusinessId(TenantContext.current().businessId());
        submission.setStaffId(staffId);
        submission.setWeekStart(weekStart);
        submission.setStatus(SubmissionStatus.DRAFT);
        return submission;
    }
}

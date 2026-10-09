package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.AttendanceRequests;
import com.fnbx.hrm.dto.response.AttendanceResponses.PayrollCell;
import com.fnbx.hrm.dto.response.AttendanceResponses.Sheet;
import com.fnbx.hrm.entity.AttendanceException;
import com.fnbx.hrm.entity.AttendanceSheet;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.AttendanceSheetStatus;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.AttendanceExceptionRepository;
import com.fnbx.hrm.repository.AttendanceSheetRepository;
import com.fnbx.hrm.repository.ShiftScheduleRepository;
import com.fnbx.hrm.service.AttendanceService;
import com.fnbx.hrm.service.attendance.AttendancePeriods;
import com.fnbx.hrm.service.attendance.AttendancePlanner;
import com.fnbx.hrm.service.attendance.AttendancePlanner.PlannedCell;
import com.fnbx.hrm.service.attendance.AttendanceTimesheetWriter;
import com.fnbx.hrm.service.attendance.AttendanceViewAssembler;
import com.fnbx.hrm.service.attendance.ShiftOutcome;
import com.fnbx.hrm.service.scheduling.ScheduleAccess;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.ScheduleContextLoader;
import com.fnbx.hrm.service.scheduling.ScheduleLoader;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceSheetRepository sheetRepository;
    private final AttendanceExceptionRepository exceptionRepository;
    private final ShiftScheduleRepository scheduleRepository;
    private final ScheduleLoader scheduleLoader;
    private final ScheduleContextLoader contextLoader;
    private final AttendanceViewAssembler viewAssembler;
    private final AttendancePlanner planner;
    private final AttendanceTimesheetWriter timesheetWriter;
    private final AttendancePeriods periods;
    private final ScheduleAccess access;

    @Override
    @Transactional
    public Sheet create(UUID scheduleId) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        access.requireEdit(schedule.getBranchId());
        return sheetRepository.findByShiftScheduleId(scheduleId).map(this::view).orElseGet(() -> {
            if (schedule.getStatus() != ScheduleStatus.PUBLISHED) {
                throw PayrollExceptions.scheduleStateInvalid("Attendance can only be created from an approved schedule");
            }
            if (!WeekCalendar.hasEnded(schedule.getWeekStart(), LocalDate.now())) {
                throw PayrollExceptions.attendanceWeekNotEnded();
            }
            return view(sheetRepository.saveAndFlush(newSheet(schedule)));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Sheet find(UUID branchId, LocalDate weekStart) {
        access.requireRead(branchId);
        ShiftSchedule schedule = scheduleRepository.findByBranchIdAndWeekStart(branchId, weekStart).orElseThrow(PayrollExceptions::scheduleNotFound);
        return view(sheetRepository.findByShiftScheduleId(schedule.getShiftScheduleId()).orElseThrow(PayrollExceptions::attendanceSheetNotFound));
    }

    @Override
    @Transactional(readOnly = true)
    public Sheet get(UUID sheetId) {
        AttendanceSheet sheet = requireSheet(sheetId);
        access.requireRead(scheduleLoader.require(sheet.getShiftScheduleId()).getBranchId());
        return view(sheet);
    }

    @Override
    @Transactional
    public Sheet mark(UUID sheetId, UUID assignmentId, AttendanceRequests.Mark request) {
        AttendanceSheet sheet = requireSheet(sheetId);
        ShiftSchedule schedule = scheduleLoader.require(sheet.getShiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        requireMarkable(sheet);
        ScheduleContext context = contextLoader.load(schedule);
        ShiftAssignment assignment = requireAssignment(context, assignmentId);
        validate(context, assignment, request);
        AttendanceException exception = exceptionRepository.findById(assignmentId).orElseGet(() -> newException(assignment));
        exception.setStatus(request.status());
        exception.setLateLevel(request.lateLevel());
        exception.setNote(request.note());
        exception.setSetBy(TenantContext.current().userId());
        exception.setSetAt(Instant.now());
        exception.setPayableHours(null);
        exceptionRepository.saveAndFlush(exception);
        sheet.setStatus(AttendanceSheetStatus.IN_REVIEW);
        return view(sheetRepository.saveAndFlush(sheet));
    }

    @Override
    @Transactional
    public Sheet clear(UUID sheetId, UUID assignmentId) {
        AttendanceSheet sheet = requireSheet(sheetId);
        access.requireEdit(scheduleLoader.require(sheet.getShiftScheduleId()).getBranchId());
        requireMarkable(sheet);
        exceptionRepository.findById(assignmentId).ifPresent(exceptionRepository::delete);
        exceptionRepository.flush();
        return view(sheet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollCell> payrollPreview(UUID sheetId) {
        AttendanceSheet sheet = requireSheet(sheetId);
        ShiftSchedule schedule = scheduleLoader.require(sheet.getShiftScheduleId());
        access.requireRead(schedule.getBranchId());
        ScheduleContext context = contextLoader.load(schedule);
        return planner.plan(context.assignments(), exceptionsOf(context)).stream()
                .filter(planned -> planned.cell().rawValue() != null)
                .map(planned -> toPayrollCell(context, planned)).toList();
    }

    @Override
    @Transactional
    public Sheet submit(UUID sheetId, AttendanceRequests.Transition request) {
        AttendanceSheet sheet = requireSheet(sheetId);
        access.requireEdit(scheduleLoader.require(sheet.getShiftScheduleId()).getBranchId());
        requireVersion(sheet, request.expectedVersion());
        requireMarkable(sheet);
        sheet.setStatus(AttendanceSheetStatus.SUBMITTED);
        sheet.setSubmittedBy(TenantContext.current().userId());
        sheet.setSubmittedAt(Instant.now());
        sheet.setReturnReason(null);
        return saveAndView(sheet);
    }

    @Override
    @Transactional
    public Sheet returnToReview(UUID sheetId, AttendanceRequests.Return request) {
        AttendanceSheet sheet = requireSheet(sheetId);
        access.requireApprove(scheduleLoader.require(sheet.getShiftScheduleId()).getBranchId());
        requireVersion(sheet, request.expectedVersion());
        requireStatus(sheet, AttendanceSheetStatus.SUBMITTED);
        sheet.setStatus(AttendanceSheetStatus.IN_REVIEW);
        sheet.setReturnReason(request.reason());
        return saveAndView(sheet);
    }

    @Override
    @Transactional
    public Sheet confirm(UUID sheetId, AttendanceRequests.Transition request) {
        AttendanceSheet sheet = requireSheet(sheetId);
        ShiftSchedule schedule = scheduleLoader.require(sheet.getShiftScheduleId());
        access.requireApprove(schedule.getBranchId());
        requireVersion(sheet, request.expectedVersion());
        requireStatus(sheet, AttendanceSheetStatus.SUBMITTED);
        ScheduleContext context = contextLoader.load(schedule);
        List<AttendanceException> exceptions = exceptionsOf(context);
        timesheetWriter.write(sheet.getAttendanceSheetId(), schedule.getBranchId(), planner.plan(context.assignments(), exceptions));
        snapshotPayableHours(context, exceptions);
        sheet.setStatus(AttendanceSheetStatus.CONFIRMED);
        sheet.setConfirmedBy(TenantContext.current().userId());
        sheet.setConfirmedAt(Instant.now());
        lock(schedule, ScheduleStatus.LOCKED);
        return saveAndView(sheet);
    }

    @Override
    @Transactional
    public Sheet reopen(UUID sheetId, AttendanceRequests.Return request) {
        AttendanceSheet sheet = requireSheet(sheetId);
        ShiftSchedule schedule = scheduleLoader.require(sheet.getShiftScheduleId());
        access.requireApprove(schedule.getBranchId());
        requireVersion(sheet, request.expectedVersion());
        requireStatus(sheet, AttendanceSheetStatus.CONFIRMED);
        requirePeriodsNotClosed(schedule);
        ScheduleContext context = contextLoader.load(schedule);
        exceptionsOf(context).forEach(exception -> exception.setPayableHours(null));
        sheet.setStatus(AttendanceSheetStatus.IN_REVIEW);
        sheet.setConfirmedBy(null);
        sheet.setConfirmedAt(null);
        sheet.setReopenedBy(TenantContext.current().userId());
        sheet.setReopenReason(request.reason());
        lock(schedule, ScheduleStatus.PUBLISHED);
        return saveAndView(sheet);
    }

    private void validate(ScheduleContext context, ShiftAssignment assignment, AttendanceRequests.Mark request) {
        boolean late = request.status() == AttendanceExceptionStatus.LATE;
        if (late != (request.lateLevel() != null)) {
            throw PayrollExceptions.invalidField("A late level is required for a late shift and not allowed for anything else");
        }
        boolean leave = request.status() == AttendanceExceptionStatus.LEAVE_PAID || request.status() == AttendanceExceptionStatus.LEAVE_UNPAID;
        SchedulingStaff person = context.person(assignment.getStaffId());
        boolean fullTime = person != null && person.typeOn(assignment.getWorkDate()).filter(type -> type == EmploymentType.FULLTIME).isPresent();
        if (leave && !fullTime) {
            throw PayrollExceptions.attendanceLeaveNotAllowed();
        }
    }

    private void snapshotPayableHours(ScheduleContext context, List<AttendanceException> exceptions) {
        exceptions.forEach(exception -> {
            ShiftAssignment assignment = context.assignments().stream()
                    .filter(candidate -> candidate.getShiftAssignmentId().equals(exception.getShiftAssignmentId())).findFirst().orElseThrow();
            exception.setPayableHours(ShiftOutcome.of(TimeWindow.of(assignment).hours(), exception).payableHours());
        });
        exceptionRepository.saveAll(exceptions);
    }

    private void requirePeriodsNotClosed(ShiftSchedule schedule) {
        for (LocalDate date : WeekCalendar.days(schedule.getWeekStart())) {
            periods.containing(date).filter(period -> period.getStatus() != PeriodStatus.DRAFT).ifPresent(period -> {
                throw PayrollExceptions.attendancePeriodClosed("The payroll period of " + date + " is " + period.getStatus());
            });
        }
    }

    private void lock(ShiftSchedule schedule, ScheduleStatus status) {
        schedule.setStatus(status);
        schedule.setVersion(schedule.getVersion() + 1);
        scheduleRepository.saveAndFlush(schedule);
    }

    private List<AttendanceException> exceptionsOf(ScheduleContext context) {
        return exceptionRepository.findByShiftAssignmentIdIn(context.assignments().stream().map(ShiftAssignment::getShiftAssignmentId).toList());
    }

    private PayrollCell toPayrollCell(ScheduleContext context, PlannedCell planned) {
        SchedulingStaff person = context.person(planned.staffId());
        var period = periods.containing(planned.date());
        return new PayrollCell(planned.staffId(), person == null ? null : person.displayName(), planned.date(),
                planned.cell().rawValue(), planned.cell().lateShifts(), period.map(p -> p.getPayrollPeriodId()).orElse(null),
                period.map(p -> p.getStatus().name()).orElse("NO_PERIOD"));
    }

    private ShiftAssignment requireAssignment(ScheduleContext context, UUID assignmentId) {
        return context.assignments().stream().filter(assignment -> assignment.getShiftAssignmentId().equals(assignmentId)).findFirst()
                .orElseThrow(PayrollExceptions::shiftAssignmentNotFound);
    }

    private AttendanceException newException(ShiftAssignment assignment) {
        AttendanceException exception = new AttendanceException();
        exception.setShiftAssignmentId(assignment.getShiftAssignmentId());
        exception.setBusinessId(assignment.getBusinessId());
        return exception;
    }

    private AttendanceSheet newSheet(ShiftSchedule schedule) {
        AttendanceSheet sheet = new AttendanceSheet();
        sheet.setAttendanceSheetId(UUID.randomUUID());
        sheet.setBusinessId(schedule.getBusinessId());
        sheet.setShiftScheduleId(schedule.getShiftScheduleId());
        sheet.setStatus(AttendanceSheetStatus.OPEN);
        return sheet;
    }

    private Sheet view(AttendanceSheet sheet) {
        ScheduleContext context = contextLoader.load(scheduleLoader.require(sheet.getShiftScheduleId()));
        return viewAssembler.assemble(sheet, context, exceptionsOf(context));
    }

    private Sheet saveAndView(AttendanceSheet sheet) {
        sheet.setVersion(sheet.getVersion() + 1);
        return view(sheetRepository.saveAndFlush(sheet));
    }

    private AttendanceSheet requireSheet(UUID sheetId) {
        return sheetRepository.findById(sheetId).orElseThrow(PayrollExceptions::attendanceSheetNotFound);
    }

    private void requireVersion(AttendanceSheet sheet, long expectedVersion) {
        if (sheet.getVersion() != expectedVersion) {
            throw PayrollExceptions.versionConflict();
        }
    }

    private void requireMarkable(AttendanceSheet sheet) {
        if (sheet.getStatus() != AttendanceSheetStatus.OPEN && sheet.getStatus() != AttendanceSheetStatus.IN_REVIEW) {
            throw PayrollExceptions.attendanceStateInvalid("The attendance sheet is " + sheet.getStatus());
        }
    }

    private void requireStatus(AttendanceSheet sheet, AttendanceSheetStatus expected) {
        if (sheet.getStatus() != expected) {
            throw PayrollExceptions.attendanceStateInvalid("The attendance sheet is " + sheet.getStatus() + ", expected " + expected);
        }
    }
}

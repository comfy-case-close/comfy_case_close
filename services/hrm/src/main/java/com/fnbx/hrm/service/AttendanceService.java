package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.AttendanceRequests;
import com.fnbx.hrm.dto.response.AttendanceResponses;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Attendance of a published week. Every shift is on time unless marked; the store manager marks and submits, the
 * general manager confirms, and confirming writes the hours into the payroll timesheet.
 */
public interface AttendanceService {

    AttendanceResponses.Sheet create(UUID scheduleId);

    AttendanceResponses.Sheet find(UUID branchId, LocalDate weekStart);

    AttendanceResponses.Sheet get(UUID sheetId);

    AttendanceResponses.Sheet mark(UUID sheetId, UUID assignmentId, AttendanceRequests.Mark request);

    AttendanceResponses.Sheet clear(UUID sheetId, UUID assignmentId);

    List<AttendanceResponses.PayrollCell> payrollPreview(UUID sheetId);

    AttendanceResponses.Sheet submit(UUID sheetId, AttendanceRequests.Transition request);

    AttendanceResponses.Sheet returnToReview(UUID sheetId, AttendanceRequests.Return request);

    AttendanceResponses.Sheet confirm(UUID sheetId, AttendanceRequests.Transition request);

    AttendanceResponses.Sheet reopen(UUID sheetId, AttendanceRequests.Return request);
}

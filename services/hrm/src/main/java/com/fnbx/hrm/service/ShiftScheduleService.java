package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.AssignmentChangeResponse;
import com.fnbx.hrm.dto.response.CandidateResponse;
import com.fnbx.hrm.dto.response.ScheduleIssueResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryTotalsResponse;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.enums.ScheduleStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The weekly schedule of a branch and its way from draft to approved. */
public interface ShiftScheduleService {

    ShiftScheduleResponse create(ScheduleRequests.Create request);

    List<ScheduleSummaryResponse> list(UUID branchId, LocalDate weekStart, ScheduleStatus status);

    ShiftScheduleResponse get(UUID scheduleId);

    ShiftScheduleResponse submit(UUID scheduleId, ScheduleRequests.Submit request);

    ShiftScheduleResponse approve(UUID scheduleId, ScheduleRequests.Approve request);

    ShiftScheduleResponse returnToDraft(UUID scheduleId, ScheduleRequests.Return request);

    List<ScheduleIssueResponse> issues(UUID scheduleId);

    List<CandidateResponse> candidates(UUID scheduleId, UUID shiftSlotId, LocalDate date, UUID positionId, String search);

    /** Changes made since the store manager sent the week for approval; all changes when it was never sent. */
    List<AssignmentChangeResponse> changes(UUID scheduleId, boolean sinceSubmitted);

    ScheduleSummaryTotalsResponse summary(UUID scheduleId);
}

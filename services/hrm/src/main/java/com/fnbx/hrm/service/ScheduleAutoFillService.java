package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.AutoFillResponses;
import java.util.UUID;

/** Fills only the places still missing; every person already on the schedule stays exactly where they are. */
public interface ScheduleAutoFillService {

    AutoFillResponses.Run autoFill(UUID scheduleId, ScheduleRequests.AutoFill request);

    AutoFillResponses.Run run(UUID runId);

    /** Removes the shifts that run created and nobody has changed since. */
    AutoFillResponses.Run undo(UUID runId);
}

package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import java.util.UUID;

/**
 * Changes to who works which shift. Before approval the store manager arranges freely; after approval only the general
 * manager may add people or change times, while the store manager may still replace or remove people.
 */
public interface ShiftAssignmentService {

    ShiftScheduleResponse.AssignmentView add(ScheduleRequests.AddAssignment request);

    ShiftScheduleResponse.AssignmentView patch(UUID assignmentId, ScheduleRequests.PatchAssignment request);

    void remove(UUID assignmentId, ScheduleRequests.Remove request);

    ShiftScheduleResponse.AssignmentView replace(UUID assignmentId, ScheduleRequests.Replace request);

    /** Applies several operations of one schedule in a single transaction; any failure undoes all of them. */
    ShiftScheduleResponse batch(UUID scheduleId, ScheduleRequests.Batch request);
}

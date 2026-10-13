package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import java.util.UUID;

public interface ShiftAssignmentService {

    ShiftScheduleResponse.AssignmentView add(ScheduleRequests.AddAssignment request);

    ShiftScheduleResponse.AssignmentView patch(UUID assignmentId, ScheduleRequests.PatchAssignment request);

    void remove(UUID assignmentId, ScheduleRequests.Remove request);

    ShiftScheduleResponse.AssignmentView replace(UUID assignmentId, ScheduleRequests.Replace request);

    java.util.List<ShiftScheduleResponse.AssignmentView> swap(UUID assignmentId, ScheduleRequests.Swap request);

    ShiftScheduleResponse batch(UUID scheduleId, ScheduleRequests.Batch request);
}

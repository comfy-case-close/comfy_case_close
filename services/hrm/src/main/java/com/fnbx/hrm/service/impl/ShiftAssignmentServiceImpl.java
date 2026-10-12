package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse.AssignmentView;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSchedule;
import com.fnbx.hrm.entity.ShiftSlot;
import com.fnbx.hrm.enums.AssignmentSource;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ShiftAssignmentRepository;
import com.fnbx.hrm.service.ShiftAssignmentService;
import com.fnbx.hrm.service.scheduling.AssignmentGuard;
import com.fnbx.hrm.service.scheduling.AssignmentStore;
import com.fnbx.hrm.service.scheduling.AssignmentStore.NewAssignment;
import com.fnbx.hrm.service.scheduling.ScheduleAccess;
import com.fnbx.hrm.service.scheduling.ScheduleContext;
import com.fnbx.hrm.service.scheduling.ScheduleContextLoader;
import com.fnbx.hrm.service.scheduling.ScheduleLoader;
import com.fnbx.hrm.service.scheduling.ScheduleStateRules;
import com.fnbx.hrm.service.scheduling.ScheduleViewAssembler;
import com.fnbx.hrm.service.scheduling.StaffingGuard;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.service.scheduling.WeekCalendar;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftAssignmentServiceImpl implements ShiftAssignmentService {

    private static final String KEPT_REASON = "Existing shift kept";

    private final ShiftAssignmentRepository assignmentRepository;
    private final ScheduleLoader scheduleLoader;
    private final ScheduleContextLoader contextLoader;
    private final ScheduleViewAssembler viewAssembler;
    private final ScheduleAccess access;
    private final AssignmentGuard guard;
    private final AssignmentStore store;
    private final StaffingGuard staffingGuard;

    @Override
    @Transactional
    public AssignmentView add(ScheduleRequests.AddAssignment request) {
        ShiftSchedule schedule = scheduleLoader.require(request.shiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        ScheduleStateRules.requireArrangeable(schedule.getStatus(), access.canApprove(schedule.getBranchId()));
        ScheduleContext context = contextLoader.load(schedule);
        ShiftSlot slot = offeredSlot(context, request.date(), request.shiftSlotId());
        TimeWindow window = windowOf(slot, request.startTime(), request.endTime());
        guard.requireAssignable(context, request.staffId(), request.date(), slot, request.positionId(), window, null, request.reason());
        ShiftAssignment created = store.add(schedule, new NewAssignment(request.staffId(), request.date(), slot,
                request.positionId(), window, AssignmentSource.MANUAL, null, request.note()), request.reason());
        return viewAssembler.view(contextLoader.load(schedule), created);
    }

    @Override
    @Transactional
    public AssignmentView patch(UUID assignmentId, ScheduleRequests.PatchAssignment request) {
        ShiftAssignment assignment = requireAssignment(assignmentId, request.expectedVersion());
        ShiftSchedule schedule = scheduleLoader.require(assignment.getShiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        boolean timeChange = request.startTime() != null || request.endTime() != null;
        boolean approver = access.canApprove(schedule.getBranchId());
        if (timeChange) {
            ScheduleStateRules.requireArrangeable(schedule.getStatus(), approver);
        } else {
            ScheduleStateRules.requireTrimmable(schedule.getStatus(), approver);
        }
        ScheduleContext context = contextLoader.load(schedule);
        ShiftSlot slot = context.slots().byId(assignment.getShiftSlotId());
        TimeWindow window = windowOf(slot,
                request.startTime() == null ? assignment.getStartTime() : request.startTime(),
                request.endTime() == null ? assignment.getEndTime() : request.endTime());
        guard.requireAssignable(context, assignment.getStaffId(), assignment.getWorkDate(), slot, assignment.getPositionId(),
                window, assignment.getShiftAssignmentId(), KEPT_REASON);
        store.changeTime(assignment, window, request.note() == null ? assignment.getNote() : request.note());
        return viewAssembler.view(contextLoader.load(schedule), assignment);
    }

    @Override
    @Transactional
    public void remove(UUID assignmentId, ScheduleRequests.Remove request) {
        ShiftAssignment assignment = requireAssignment(assignmentId, request.expectedVersion());
        ShiftSchedule schedule = scheduleLoader.require(assignment.getShiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        boolean approver = access.canApprove(schedule.getBranchId());
        ScheduleStateRules.requireTrimmable(schedule.getStatus(), approver);
        if (ScheduleStateRules.needsMinimumStaffingCheck(schedule.getStatus(), approver)) {
            staffingGuard.requireStillCovered(contextLoader.load(schedule), assignment);
        }
        store.remove(assignment, request.reason());
    }

    @Override
    @Transactional
    public AssignmentView replace(UUID assignmentId, ScheduleRequests.Replace request) {
        ShiftAssignment assignment = requireAssignment(assignmentId, request.expectedVersion());
        ShiftSchedule schedule = scheduleLoader.require(assignment.getShiftScheduleId());
        access.requireEdit(schedule.getBranchId());
        ScheduleStateRules.requireTrimmable(schedule.getStatus(), access.canApprove(schedule.getBranchId()));
        ScheduleContext context = contextLoader.load(schedule);
        guard.requireAssignable(context, request.newStaffId(), assignment.getWorkDate(), context.slots().byId(assignment.getShiftSlotId()),
                assignment.getPositionId(), TimeWindow.of(assignment), assignment.getShiftAssignmentId(), request.reason());
        store.replace(assignment, request.newStaffId(), request.reason());
        return viewAssembler.view(contextLoader.load(schedule), assignment);
    }

    @Override
    @Transactional
    public ShiftScheduleResponse batch(UUID scheduleId, ScheduleRequests.Batch request) {
        ShiftSchedule schedule = scheduleLoader.require(scheduleId);
        request.operations().forEach(operation -> apply(scheduleId, operation));
        return viewAssembler.assemble(contextLoader.load(schedule));
    }

    private void apply(UUID scheduleId, ScheduleRequests.BatchOperation operation) {
        switch (operation.type()) {
            case ADD -> add(withSchedule(requireNonNull(operation.add()), scheduleId));
            case REPLACE -> replace(requireNonNull(operation.assignmentId()), requireNonNull(operation.replace()));
            case REMOVE -> remove(requireNonNull(operation.assignmentId()), requireNonNull(operation.remove()));
        }
    }

    private ScheduleRequests.AddAssignment withSchedule(ScheduleRequests.AddAssignment add, UUID scheduleId) {
        if (!add.shiftScheduleId().equals(scheduleId)) {
            throw PayrollExceptions.invalidField("Every operation of a batch must belong to the batch schedule");
        }
        return add;
    }

    private <T> T requireNonNull(T value) {
        if (value == null) {
            throw PayrollExceptions.invalidField("The batch operation is incomplete");
        }
        return value;
    }

    private ShiftAssignment requireAssignment(UUID assignmentId, long expectedVersion) {
        ShiftAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow(PayrollExceptions::shiftAssignmentNotFound);
        if (assignment.getVersion() != expectedVersion) {
            throw PayrollExceptions.versionConflict();
        }
        return assignment;
    }

    private ShiftSlot offeredSlot(ScheduleContext context, LocalDate date, UUID shiftSlotId) {
        LocalDate weekStart = context.schedule().getWeekStart();
        if (date.isBefore(weekStart) || date.isAfter(WeekCalendar.lastDay(weekStart))) {
            throw PayrollExceptions.invalidField("The date is outside the schedule week");
        }
        return context.slots().on(date).stream().filter(slot -> slot.getShiftSlotId().equals(shiftSlotId)).findFirst()
                .orElseThrow(() -> PayrollExceptions.invalidField("This shift is not offered on " + date));
    }

    private TimeWindow windowOf(ShiftSlot slot, LocalTime start, LocalTime end) {
        TimeWindow window = new TimeWindow(start == null ? slot.getStartTime() : start, end == null ? slot.getEndTime() : end);
        if (!window.end().isAfter(window.start())) {
            throw PayrollExceptions.invalidField("The shift must end after it starts");
        }
        return window;
    }
}

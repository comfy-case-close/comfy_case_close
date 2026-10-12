package com.fnbx.hrm.service.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftAssignmentEvent;
import com.fnbx.hrm.enums.AssignmentEventType;
import com.fnbx.hrm.repository.ShiftAssignmentEventRepository;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentEventRecorder {

    private final ShiftAssignmentEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public void record(ShiftAssignment assignment, AssignmentEventType type, Map<String, Object> before,
            Map<String, Object> after, String reason) {
        ShiftAssignmentEvent event = new ShiftAssignmentEvent();
        event.setShiftAssignmentEventId(UUID.randomUUID());
        event.setBusinessId(assignment.getBusinessId());
        event.setShiftScheduleId(assignment.getShiftScheduleId());
        event.setShiftAssignmentId(assignment.getShiftAssignmentId());
        event.setStaffId(assignment.getStaffId());
        event.setWorkDate(assignment.getWorkDate());
        event.setEventType(type);
        event.setActorId(TenantContext.current().userId());
        event.setBeforeValue(before == null ? null : json(before));
        event.setAfterValue(after == null ? null : json(after));
        event.setReason(reason);
        event.setOccurredAt(Instant.now());
        eventRepository.save(event);
    }

    public Map<String, Object> snapshot(ShiftAssignment assignment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("staffId", assignment.getStaffId());
        snapshot.put("shiftSlotId", assignment.getShiftSlotId());
        snapshot.put("positionId", assignment.getPositionId());
        snapshot.put("startTime", assignment.getStartTime().toString());
        snapshot.put("endTime", assignment.getEndTime().toString());
        return snapshot;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize the assignment change", ex);
        }
    }
}

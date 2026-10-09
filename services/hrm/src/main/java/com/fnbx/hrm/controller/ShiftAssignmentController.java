package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.service.ShiftAssignmentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShiftAssignmentController {

    private final ShiftAssignmentService assignmentService;

    @PostMapping("/shift-assignments")
    public ResponseEntity<ShiftScheduleResponse.AssignmentView> add(@Valid @RequestBody ScheduleRequests.AddAssignment request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.add(request));
    }

    @PatchMapping("/shift-assignments/{assignmentId}")
    public ResponseEntity<ShiftScheduleResponse.AssignmentView> patch(@PathVariable UUID assignmentId,
            @Valid @RequestBody ScheduleRequests.PatchAssignment request) {
        return ResponseEntity.ok(assignmentService.patch(assignmentId, request));
    }

    @DeleteMapping("/shift-assignments/{assignmentId}")
    public ResponseEntity<Void> remove(@PathVariable UUID assignmentId, @RequestParam long expectedVersion,
            @RequestParam(required = false) String reason) {
        assignmentService.remove(assignmentId, new ScheduleRequests.Remove(expectedVersion, reason));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shift-assignments/{assignmentId}/replace")
    public ResponseEntity<ShiftScheduleResponse.AssignmentView> replace(@PathVariable UUID assignmentId,
            @Valid @RequestBody ScheduleRequests.Replace request) {
        return ResponseEntity.ok(assignmentService.replace(assignmentId, request));
    }
}

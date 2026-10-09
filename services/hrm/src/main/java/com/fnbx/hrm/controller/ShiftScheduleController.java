package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.ScheduleRequests;
import com.fnbx.hrm.dto.response.AssignmentChangeResponse;
import com.fnbx.hrm.dto.response.AttendanceResponses;
import com.fnbx.hrm.dto.response.AutoFillResponses;
import com.fnbx.hrm.dto.response.CandidateResponse;
import com.fnbx.hrm.dto.response.ScheduleIssueResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryResponse;
import com.fnbx.hrm.dto.response.ScheduleSummaryTotalsResponse;
import com.fnbx.hrm.dto.response.ShiftScheduleResponse;
import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.service.AttendanceService;
import com.fnbx.hrm.service.ScheduleAutoFillService;
import com.fnbx.hrm.service.ShiftAssignmentService;
import com.fnbx.hrm.service.ShiftScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The weekly schedule of a branch: arrange, auto-fill, send for approval, approve, and start attendance. */
@RestController
@RequiredArgsConstructor
public class ShiftScheduleController {

    private static final String DATE = "yyyy-MM-dd";

    private final ShiftScheduleService scheduleService;
    private final ScheduleAutoFillService autoFillService;
    private final ShiftAssignmentService assignmentService;
    private final AttendanceService attendanceService;

    @PostMapping("/shift-schedules")
    public ResponseEntity<ShiftScheduleResponse> create(@Valid @RequestBody ScheduleRequests.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @GetMapping("/shift-schedules")
    public ResponseEntity<List<ScheduleSummaryResponse>> list(@RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate weekStart,
            @RequestParam(required = false) ScheduleStatus status) {
        return ResponseEntity.ok(scheduleService.list(branchId, weekStart, status));
    }

    @GetMapping("/shift-schedules/{scheduleId}")
    public ResponseEntity<ShiftScheduleResponse> get(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(scheduleService.get(scheduleId));
    }

    @PostMapping("/shift-schedules/{scheduleId}/auto-fill")
    public ResponseEntity<AutoFillResponses.Run> autoFill(@PathVariable UUID scheduleId,
            @RequestBody(required = false) ScheduleRequests.AutoFill request) {
        return ResponseEntity.ok(autoFillService.autoFill(scheduleId, request == null ? new ScheduleRequests.AutoFill(true) : request));
    }

    @GetMapping("/schedule-generation-runs/{runId}")
    public ResponseEntity<AutoFillResponses.Run> run(@PathVariable UUID runId) {
        return ResponseEntity.ok(autoFillService.run(runId));
    }

    @PostMapping("/schedule-generation-runs/{runId}/undo")
    public ResponseEntity<AutoFillResponses.Run> undo(@PathVariable UUID runId) {
        return ResponseEntity.ok(autoFillService.undo(runId));
    }

    @GetMapping("/shift-schedules/{scheduleId}/issues")
    public ResponseEntity<List<ScheduleIssueResponse>> issues(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(scheduleService.issues(scheduleId));
    }

    @GetMapping("/shift-schedules/{scheduleId}/candidates")
    public ResponseEntity<List<CandidateResponse>> candidates(@PathVariable UUID scheduleId, @RequestParam UUID shiftSlotId,
            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate date, @RequestParam UUID positionId,
            @RequestParam(name = "q", required = false) String search) {
        return ResponseEntity.ok(scheduleService.candidates(scheduleId, shiftSlotId, date, positionId, search));
    }

    @PostMapping("/shift-schedules/{scheduleId}/assignments:batch")
    public ResponseEntity<ShiftScheduleResponse> batch(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequests.Batch request) {
        return ResponseEntity.ok(assignmentService.batch(scheduleId, request));
    }

    @PostMapping("/shift-schedules/{scheduleId}/submit")
    public ResponseEntity<ShiftScheduleResponse> submit(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequests.Submit request) {
        return ResponseEntity.ok(scheduleService.submit(scheduleId, request));
    }

    @PostMapping("/shift-schedules/{scheduleId}/approve")
    public ResponseEntity<ShiftScheduleResponse> approve(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequests.Approve request) {
        return ResponseEntity.ok(scheduleService.approve(scheduleId, request));
    }

    @PostMapping("/shift-schedules/{scheduleId}/return")
    public ResponseEntity<ShiftScheduleResponse> returnToDraft(@PathVariable UUID scheduleId, @Valid @RequestBody ScheduleRequests.Return request) {
        return ResponseEntity.ok(scheduleService.returnToDraft(scheduleId, request));
    }

    @GetMapping("/shift-schedules/{scheduleId}/changes")
    public ResponseEntity<List<AssignmentChangeResponse>> changes(@PathVariable UUID scheduleId,
            @RequestParam(defaultValue = "submitted") String since) {
        return ResponseEntity.ok(scheduleService.changes(scheduleId, "submitted".equals(since)));
    }

    @GetMapping("/shift-schedules/{scheduleId}/summary")
    public ResponseEntity<ScheduleSummaryTotalsResponse> summary(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(scheduleService.summary(scheduleId));
    }

    @PostMapping("/shift-schedules/{scheduleId}/attendance-sheet")
    public ResponseEntity<AttendanceResponses.Sheet> createAttendanceSheet(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(attendanceService.create(scheduleId));
    }
}

package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.AttendanceRequests;
import com.fnbx.hrm.dto.response.AttendanceResponses;
import com.fnbx.hrm.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Attendance created from the published schedule; only exceptions are marked and nobody types hours. */
@RestController
@RequestMapping("/attendance-sheets")
@RequiredArgsConstructor
public class AttendanceSheetController {

    private final AttendanceService attendanceService;

    @GetMapping
    public ResponseEntity<AttendanceResponses.Sheet> find(@RequestParam UUID branchId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate weekStart) {
        return ResponseEntity.ok(attendanceService.find(branchId, weekStart));
    }

    @GetMapping("/{sheetId}")
    public ResponseEntity<AttendanceResponses.Sheet> get(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(attendanceService.get(sheetId));
    }

    @PutMapping("/{sheetId}/exceptions/{assignmentId}")
    public ResponseEntity<AttendanceResponses.Sheet> mark(@PathVariable UUID sheetId, @PathVariable UUID assignmentId,
            @Valid @RequestBody AttendanceRequests.Mark request) {
        return ResponseEntity.ok(attendanceService.mark(sheetId, assignmentId, request));
    }

    @DeleteMapping("/{sheetId}/exceptions/{assignmentId}")
    public ResponseEntity<AttendanceResponses.Sheet> clear(@PathVariable UUID sheetId, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(attendanceService.clear(sheetId, assignmentId));
    }

    @GetMapping("/{sheetId}/payroll-preview")
    public ResponseEntity<List<AttendanceResponses.PayrollCell>> payrollPreview(@PathVariable UUID sheetId) {
        return ResponseEntity.ok(attendanceService.payrollPreview(sheetId));
    }

    @PostMapping("/{sheetId}/submit")
    public ResponseEntity<AttendanceResponses.Sheet> submit(@PathVariable UUID sheetId, @Valid @RequestBody AttendanceRequests.Transition request) {
        return ResponseEntity.ok(attendanceService.submit(sheetId, request));
    }

    @PostMapping("/{sheetId}/return")
    public ResponseEntity<AttendanceResponses.Sheet> returnToReview(@PathVariable UUID sheetId, @Valid @RequestBody AttendanceRequests.Return request) {
        return ResponseEntity.ok(attendanceService.returnToReview(sheetId, request));
    }

    @PostMapping("/{sheetId}/confirm")
    public ResponseEntity<AttendanceResponses.Sheet> confirm(@PathVariable UUID sheetId, @Valid @RequestBody AttendanceRequests.Transition request) {
        return ResponseEntity.ok(attendanceService.confirm(sheetId, request));
    }

    @PostMapping("/{sheetId}/reopen")
    public ResponseEntity<AttendanceResponses.Sheet> reopen(@PathVariable UUID sheetId, @Valid @RequestBody AttendanceRequests.Return request) {
        return ResponseEntity.ok(attendanceService.reopen(sheetId, request));
    }
}

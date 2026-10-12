package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.AttendanceInputPreviewRequest;
import com.fnbx.hrm.dto.request.BulkTimesheetUpdateRequest;
import com.fnbx.hrm.dto.response.AttendanceInputPreviewResponse;
import com.fnbx.hrm.dto.response.BulkTimesheetUpdateResponse;
import com.fnbx.hrm.dto.response.TimesheetCellResponse;
import com.fnbx.hrm.dto.response.TimesheetGridResponse;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.service.TimesheetService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The day-by-day attendance grid of a payroll period: what each person worked, coded as hours or a code. */
@RestController
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;

    @GetMapping("/payroll-periods/{payrollPeriodId}/timesheet")
    public ResponseEntity<TimesheetGridResponse> getGrid(@PathVariable UUID payrollPeriodId,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(timesheetService.getGrid(payrollPeriodId, employmentType, branchId));
    }

    @PutMapping("/payroll-periods/{payrollPeriodId}/timesheet")
    public ResponseEntity<BulkTimesheetUpdateResponse> updateCells(@PathVariable UUID payrollPeriodId,
            @Valid @RequestBody BulkTimesheetUpdateRequest request) {
        return ResponseEntity.ok(timesheetService.saveCells(payrollPeriodId, request));
    }

    @PutMapping("/payroll-lines/{payrollLineId}/timesheet/{workDate}")
    public ResponseEntity<TimesheetCellResponse> updateCell(@PathVariable UUID payrollLineId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam String rawValue, @RequestParam long expectedVersion,
            @RequestParam(required = false) String adjustReason) {
        return ResponseEntity.ok(timesheetService.saveCell(payrollLineId, workDate, rawValue, expectedVersion, adjustReason));
    }

    @DeleteMapping("/payroll-lines/{payrollLineId}/timesheet/{workDate}")
    public ResponseEntity<Void> clearCell(@PathVariable UUID payrollLineId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) String reason) {
        timesheetService.deleteCell(payrollLineId, workDate, reason);
        return ResponseEntity.noContent().build();
    }

    /** Shows how a typed value (8, CP, T1-7,5...) would be read, without saving anything. */
    @PostMapping("/timesheet/attendance-input-preview")
    public ResponseEntity<AttendanceInputPreviewResponse> previewAttendanceInput(
            @Valid @RequestBody AttendanceInputPreviewRequest request) {
        return ResponseEntity.ok(timesheetService.previewAttendanceInput(request));
    }
}

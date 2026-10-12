package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.AttendanceCodeRequest;
import com.fnbx.hrm.dto.response.AttendanceCodeResponse;
import com.fnbx.hrm.service.AttendanceCodeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The codes a timesheet cell may hold besides plain hours (CP paid leave, KL unpaid leave...). */
@RestController
@RequestMapping("/attendance-codes")
@RequiredArgsConstructor
public class AttendanceCodeController {

    private final AttendanceCodeService attendanceCodeService;

    @GetMapping
    public ResponseEntity<List<AttendanceCodeResponse>> list() {
        return ResponseEntity.ok(attendanceCodeService.list());
    }

    @PostMapping
    public ResponseEntity<AttendanceCodeResponse> create(@Valid @RequestBody AttendanceCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceCodeService.create(request));
    }

    @PutMapping("/{attendanceCodeId}")
    public ResponseEntity<AttendanceCodeResponse> update(@PathVariable UUID attendanceCodeId,
            @Valid @RequestBody AttendanceCodeRequest request) {
        return ResponseEntity.ok(attendanceCodeService.update(attendanceCodeId, request));
    }
}

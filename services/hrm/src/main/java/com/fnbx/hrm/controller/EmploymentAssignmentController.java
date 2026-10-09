package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.dto.request.EndEmploymentAssignmentRequest;
import com.fnbx.hrm.dto.request.SalaryPreviewRequest;
import com.fnbx.hrm.dto.response.EmploymentAssignmentResponse;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse;
import com.fnbx.hrm.service.EmploymentAssignmentService;
import com.fnbx.hrm.service.SalaryStructureService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** A person holding one position, at one default branch, on full-time or part-time terms, with pay rates. */
@RestController
@RequiredArgsConstructor
public class EmploymentAssignmentController {

    private final EmploymentAssignmentService assignmentService;
    private final SalaryStructureService salaryStructureService;

    @GetMapping("/employees/{staffId}/employment-assignments")
    public ResponseEntity<List<EmploymentAssignmentResponse>> listByEmployee(@PathVariable UUID staffId) {
        return ResponseEntity.ok(assignmentService.listByEmployee(staffId));
    }

    @PostMapping("/employees/{staffId}/employment-assignments")
    public ResponseEntity<EmploymentAssignmentResponse> create(@PathVariable UUID staffId,
            @Valid @RequestBody EmploymentAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(staffId, request));
    }

    @PutMapping("/employment-assignments/{assignmentId}")
    public ResponseEntity<EmploymentAssignmentResponse> update(@PathVariable UUID assignmentId,
            @Valid @RequestBody EmploymentAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.update(assignmentId, request));
    }

    @PostMapping("/employment-assignments/{assignmentId}/end")
    public ResponseEntity<EmploymentAssignmentResponse> end(@PathVariable UUID assignmentId,
            @Valid @RequestBody EndEmploymentAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.end(assignmentId, request));
    }

    @PostMapping("/employment-assignments/salary-structure/preview")
    public ResponseEntity<SalaryPreviewResponse> previewSalaryStructure(@Valid @RequestBody SalaryPreviewRequest request) {
        return ResponseEntity.ok(salaryStructureService.preview(request));
    }
}

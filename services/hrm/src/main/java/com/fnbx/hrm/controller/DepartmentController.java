package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.DepartmentRequest;
import com.fnbx.hrm.dto.response.DepartmentResponse;
import com.fnbx.hrm.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Groups positions for reporting (Store Operations, Product, Marketing...). */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> list() {
        return ResponseEntity.ok(departmentService.list());
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.create(request));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<DepartmentResponse> update(@PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(departmentId, request));
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID departmentId) {
        departmentService.deactivate(departmentId);
        return ResponseEntity.noContent().build();
    }
}

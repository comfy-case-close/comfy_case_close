package com.fnbx.identity.controller;

import java.util.List;
import java.util.UUID;
import com.fnbx.identity.dto.request.DepartmentRequest;
import com.fnbx.identity.dto.response.DepartmentResponse;
import com.fnbx.identity.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The business's departments (Store Operations, Product, Marketing...). Every route takes its business from the
 * verified JWT; none accepts a {@code businessId}. Authorization is decided in the service.
 */
@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) { this.departments = departments; }

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> list() {
        return ResponseEntity.ok(departments.list());
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departments.create(request));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<DepartmentResponse> update(@PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departments.update(departmentId, request));
    }

    /** Deactivates rather than deletes: positions and staff keep pointing at the department. */
    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID departmentId) {
        departments.deactivate(departmentId);
        return ResponseEntity.noContent().build();
    }
}

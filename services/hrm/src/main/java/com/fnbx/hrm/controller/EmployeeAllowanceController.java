package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.EmployeeAllowanceRequest;
import com.fnbx.hrm.dto.response.EmployeeAllowanceResponse;
import com.fnbx.hrm.service.EmployeeAllowanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Lunch, housing, phone and fuel allowances paid to a person regardless of how many assignments they hold. */
@RestController
@RequestMapping("/employees/{staffId}/allowances")
@RequiredArgsConstructor
public class EmployeeAllowanceController {

    private final EmployeeAllowanceService allowanceService;

    @GetMapping
    public ResponseEntity<List<EmployeeAllowanceResponse>> listByEmployee(@PathVariable UUID staffId) {
        return ResponseEntity.ok(allowanceService.listByEmployee(staffId));
    }

    @PostMapping
    public ResponseEntity<EmployeeAllowanceResponse> create(@PathVariable UUID staffId,
            @Valid @RequestBody EmployeeAllowanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(allowanceService.create(staffId, request));
    }
}

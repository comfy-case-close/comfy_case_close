package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.InsuranceSchemeRequest;
import com.fnbx.hrm.dto.response.InsuranceSchemeResponse;
import com.fnbx.hrm.service.InsuranceSchemeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Social, health and unemployment insurance rates for employer and employee. */
@RestController
@RequestMapping("/insurance-schemes")
@RequiredArgsConstructor
public class InsuranceSchemeController {

    private final InsuranceSchemeService insuranceSchemeService;

    @GetMapping
    public ResponseEntity<List<InsuranceSchemeResponse>> list() {
        return ResponseEntity.ok(insuranceSchemeService.list());
    }

    @PostMapping
    public ResponseEntity<InsuranceSchemeResponse> create(@Valid @RequestBody InsuranceSchemeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(insuranceSchemeService.create(request));
    }
}

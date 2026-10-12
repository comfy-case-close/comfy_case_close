package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.PayrollConfigRequest;
import com.fnbx.hrm.dto.response.PayrollConfigResponse;
import com.fnbx.hrm.service.PayrollConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Company-wide calculation parameters: standard days and hours, overtime and weekend multipliers. */
@RestController
@RequestMapping("/payroll-configs")
@RequiredArgsConstructor
public class PayrollConfigController {

    private final PayrollConfigService payrollConfigService;

    @GetMapping
    public ResponseEntity<List<PayrollConfigResponse>> list() {
        return ResponseEntity.ok(payrollConfigService.list());
    }

    @GetMapping("/current")
    public ResponseEntity<PayrollConfigResponse> getCurrent() {
        return ResponseEntity.ok(payrollConfigService.getCurrent());
    }

    @PostMapping
    public ResponseEntity<PayrollConfigResponse> create(@Valid @RequestBody PayrollConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollConfigService.create(request));
    }
}

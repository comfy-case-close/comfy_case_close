package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.PayComponentUpdateRequest;
import com.fnbx.hrm.dto.response.PayComponentResponse;
import com.fnbx.hrm.service.PayComponentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The catalogue of earnings and deductions a payslip is built from (BASE_PAY, OT_PAY, BONUS, ADVANCE...). */
@RestController
@RequestMapping("/pay-components")
@RequiredArgsConstructor
public class PayComponentController {

    private final PayComponentService payComponentService;

    @GetMapping
    public ResponseEntity<List<PayComponentResponse>> list() {
        return ResponseEntity.ok(payComponentService.list());
    }

    @PutMapping("/{payComponentId}")
    public ResponseEntity<PayComponentResponse> update(@PathVariable UUID payComponentId,
            @Valid @RequestBody PayComponentUpdateRequest request) {
        return ResponseEntity.ok(payComponentService.update(payComponentId, request));
    }
}

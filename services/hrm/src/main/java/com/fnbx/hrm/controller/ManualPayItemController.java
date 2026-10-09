package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.ManualPayItemRequest;
import com.fnbx.hrm.dto.response.ManualPayItemResponse;
import com.fnbx.hrm.service.ManualPayItemService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Amounts typed in by HR instead of calculated: bonus, salary advance, performance adjustment. */
@RestController
@RequiredArgsConstructor
public class ManualPayItemController {

    private final ManualPayItemService manualPayItemService;

    @GetMapping("/payroll-lines/{payrollLineId}/manual-pay-items")
    public ResponseEntity<List<ManualPayItemResponse>> list(@PathVariable UUID payrollLineId) {
        return ResponseEntity.ok(manualPayItemService.list(payrollLineId));
    }

    @PostMapping("/payroll-lines/{payrollLineId}/manual-pay-items")
    public ResponseEntity<ManualPayItemResponse> create(@PathVariable UUID payrollLineId,
            @Valid @RequestBody ManualPayItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manualPayItemService.create(payrollLineId, request));
    }

    @PutMapping("/manual-pay-items/{payrollLineItemId}")
    public ResponseEntity<ManualPayItemResponse> update(@PathVariable UUID payrollLineItemId,
            @Valid @RequestBody ManualPayItemRequest request) {
        return ResponseEntity.ok(manualPayItemService.update(payrollLineItemId, request));
    }

    @DeleteMapping("/manual-pay-items/{payrollLineItemId}")
    public ResponseEntity<Void> delete(@PathVariable UUID payrollLineItemId) {
        manualPayItemService.delete(payrollLineItemId);
        return ResponseEntity.noContent().build();
    }
}

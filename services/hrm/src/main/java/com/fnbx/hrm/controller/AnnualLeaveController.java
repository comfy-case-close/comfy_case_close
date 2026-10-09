package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.AnnualLeaveQuotaRequest;
import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.AnnualLeaveQuotaResponse;
import com.fnbx.hrm.service.AnnualLeaveService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Paid annual-leave entitlement per person and calendar year, and how much of it is left. */
@RestController
@RequestMapping("/employees/{staffId}/annual-leave/{year}")
@RequiredArgsConstructor
public class AnnualLeaveController {

    private final AnnualLeaveService annualLeaveService;

    @GetMapping("/quota")
    public ResponseEntity<AnnualLeaveQuotaResponse> getQuota(@PathVariable UUID staffId, @PathVariable short year) {
        return ResponseEntity.ok(annualLeaveService.getQuota(staffId, year));
    }

    @PutMapping("/quota")
    public ResponseEntity<AnnualLeaveQuotaResponse> setQuota(@PathVariable UUID staffId, @PathVariable short year,
            @Valid @RequestBody AnnualLeaveQuotaRequest request) {
        return ResponseEntity.ok(annualLeaveService.setQuota(staffId, year, request));
    }

    @GetMapping("/balance")
    public ResponseEntity<AnnualLeaveBalanceResponse> getBalance(@PathVariable UUID staffId, @PathVariable short year) {
        return ResponseEntity.ok(annualLeaveService.getBalance(staffId, year));
    }
}

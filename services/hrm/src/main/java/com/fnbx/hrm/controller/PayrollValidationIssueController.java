package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.AcknowledgeValidationIssueRequest;
import com.fnbx.hrm.dto.response.PayrollValidationIssueResponse;
import com.fnbx.hrm.service.PayrollValidationIssueService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Problems the engine found in a period's data; errors block the run, warnings must be acknowledged to lock. */
@RestController
@RequiredArgsConstructor
public class PayrollValidationIssueController {

    private final PayrollValidationIssueService validationIssueService;

    @GetMapping("/payroll-periods/{payrollPeriodId}/validation-issues")
    public ResponseEntity<List<PayrollValidationIssueResponse>> list(@PathVariable UUID payrollPeriodId) {
        return ResponseEntity.ok(validationIssueService.list(payrollPeriodId));
    }

    @PostMapping("/validation-issues/{validationIssueId}/acknowledge")
    public ResponseEntity<PayrollValidationIssueResponse> acknowledge(@PathVariable UUID validationIssueId,
            @Valid @RequestBody AcknowledgeValidationIssueRequest request) {
        return ResponseEntity.ok(validationIssueService.acknowledge(validationIssueId, request));
    }
}

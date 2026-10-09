package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.PayrollAuditLogResponse;
import com.fnbx.hrm.service.PayrollAuditLogService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Who changed what in payroll data, read from the payroll audit log. */
@RestController
@RequestMapping("/payroll-audit-logs")
@RequiredArgsConstructor
public class PayrollAuditLogController {

    private final PayrollAuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<PayrollAuditLogResponse>> searchAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(auditLogService.searchAuditLogs(entityType, entityId, from, to));
    }
}

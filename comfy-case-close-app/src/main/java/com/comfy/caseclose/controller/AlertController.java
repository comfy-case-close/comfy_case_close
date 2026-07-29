package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.AlertRequest;
import com.comfy.caseclose.dto.response.AlertResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AlertController - endpoints for alert management.
 * Maps to: POST /api/v1/alerts, GET /api/v1/alerts, GET /api/v1/alerts/{id},
 *          POST /api/v1/alerts/{id}/resolve, POST /api/v1/alerts/{id}/dismiss
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * POST /api/v1/alerts - Create a new alert.
     */
    @PostMapping
    public ResponseEntity<AlertResponseDTO> createAlert(@Valid @RequestBody AlertRequest request) {
        AlertResponseDTO alert = alertService.createAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    /**
     * GET /api/v1/alerts - List alerts.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<AlertResponseDTO>> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(alertService.listAlerts(status, pageable));
    }

    /**
     * GET /api/v1/alerts/{id} - Get an alert by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlertResponseDTO> getAlertById(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.getAlertById(id));
    }

    /**
     * POST /api/v1/alerts/{id}/resolve - Mark an alert as resolved.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveAlert(@PathVariable Long id) {
        alertService.resolveAlert(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * POST /api/v1/alerts/{id}/dismiss - Mark an alert as dismissed.
     */
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismissAlert(@PathVariable Long id) {
        alertService.dismissAlert(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

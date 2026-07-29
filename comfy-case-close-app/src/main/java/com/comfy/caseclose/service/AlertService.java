package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.AlertRequest;
import com.comfy.caseclose.dto.response.AlertResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface AlertService {

    /**
     * Create and send an alert to specified recipients.
     */
    AlertResponseDTO createAlert(AlertRequest request);

    /**
     * Get an alert by ID.
     */
    AlertResponseDTO getAlertById(Long id);

    /**
     * List alerts (filterable by status).
     */
    PagedResponse<AlertResponseDTO> listAlerts(String status, Pageable pageable);

    /**
     * Mark an alert as resolved.
     */
    void resolveAlert(Long id);

    /**
     * Mark an alert as dismissed.
     */
    void dismissAlert(Long id);
}

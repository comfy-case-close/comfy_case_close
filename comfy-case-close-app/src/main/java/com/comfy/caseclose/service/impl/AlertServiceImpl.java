package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.AlertRequest;
import com.comfy.caseclose.dto.response.AlertResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.entity.Alert;
import com.comfy.caseclose.entity.AlertRecipient;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.AlertRecipientRepository;
import com.comfy.caseclose.repository.AlertRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.service.AlertService;
import com.comfy.caseclose.utils.PaginationUtils;
import com.comfy.caseclose.utils.enums.AlertChannel;
import com.comfy.caseclose.utils.enums.AlertSeverity;
import com.comfy.caseclose.utils.enums.AlertStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AlertRecipientRepository alertRecipientRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AlertResponseDTO createAlert(AlertRequest request) {
        Alert alert = alertRepository.save(buildAlert(request));
        request.getRecipientUserIds().forEach(userId -> addRecipient(alert, userId));
        return toResponseDTO(alert);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertResponseDTO getAlertById(Long id) {
        return alertRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AlertResponseDTO> listAlerts(String status, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return PaginationUtils.toPagedResponse(
                    alertRepository.findByStatus(AlertStatus.valueOf(status), pageable),
                    this::toResponseDTO);
        }
        return PaginationUtils.toPagedResponse(alertRepository.findAll(pageable), this::toResponseDTO);
    }

    @Override
    @Transactional
    public void resolveAlert(Long id) {
        setStatus(id, AlertStatus.SENT);
    }

    @Override
    @Transactional
    public void dismissAlert(Long id) {
        setStatus(id, AlertStatus.FAILED);
    }

    private Alert buildAlert(AlertRequest request) {
        Alert alert = new Alert();
        alert.setChannel(AlertChannel.valueOf(request.getChannel()));
        alert.setSeverity(AlertSeverity.valueOf(request.getSeverity()));
        alert.setStatus(AlertStatus.PENDING);
        alert.setMessage(request.getMessage());
        return alert;
    }

    private void addRecipient(Alert alert, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
        AlertRecipient recipient = new AlertRecipient();
        recipient.setAlert(alert);
        recipient.setUser(user);
        alertRecipientRepository.save(recipient);
    }

    private void setStatus(Long id, AlertStatus status) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id " + id));
        alert.setStatus(status);
    }

    private AlertResponseDTO toResponseDTO(Alert alert) {
        return AlertResponseDTO.builder()
                .id(alert.getId())
                .channel(alert.getChannel().name())
                .severity(alert.getSeverity().name())
                .status(alert.getStatus().name())
                .message(alert.getMessage())
                .cashCloseId(alert.getCashClose() == null ? null : alert.getCashClose().getId())
                .createdAt(alert.getCreatedAt())
                .recipientUserIds(alertRecipientRepository.findUserIdsByAlertId(alert.getId()))
                .build();
    }
}

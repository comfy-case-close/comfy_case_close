package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.ApprovalRequest;
import com.comfy.caseclose.dto.response.ApprovalResponseDTO;
import com.comfy.caseclose.entity.Approval;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.ApprovalRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.ApprovalService;
import com.comfy.caseclose.utils.enums.ApprovalAction;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final CashCloseRepository cashCloseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void approveCashClose(Long cashCloseId, ApprovalRequest request) {
        CashClose cashClose = requireReviewable(cashCloseId);
        transition(cashClose, ApprovalAction.APPROVE, CashCloseStatus.APPROVED, request.getNote());
    }

    @Override
    @Transactional
    public void rejectCashClose(Long cashCloseId, ApprovalRequest request) {
        String note = requireNote(request.getNote());
        CashClose cashClose = requireReviewable(cashCloseId);
        transition(cashClose, ApprovalAction.REJECT, CashCloseStatus.REJECTED, note);
    }

    @Override
    @Transactional
    public void voidCashClose(Long cashCloseId, ApprovalRequest request) {
        CashClose cashClose = requireNotVoided(cashCloseId);
        transition(cashClose, ApprovalAction.VOID, CashCloseStatus.VOIDED, request.getNote());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalResponseDTO> getApprovalHistory(Long cashCloseId) {
        return approvalRepository.findByCashCloseIdOrderByReviewedAtDesc(cashCloseId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    private void transition(CashClose cashClose, ApprovalAction action, CashCloseStatus targetStatus, String note) {
        approvalRepository.save(buildApproval(cashClose, action, targetStatus, note));
        cashClose.setStatus(targetStatus);
    }

    private Approval buildApproval(CashClose cashClose, ApprovalAction action, CashCloseStatus targetStatus, String note) {
        Approval approval = new Approval();
        approval.setCashClose(cashClose);
        approval.setAction(action);
        approval.setNote(note);
        approval.setReviewedBy(currentReviewer());
        approval.setReviewedAt(OffsetDateTime.now());
        approval.setOldStatus(cashClose.getStatus());
        approval.setNewStatus(targetStatus);
        return approval;
    }

    /**
     * SUBMITTED (LOW/MEDIUM risk) and PENDING_REVIEW (HIGH/CRITICAL risk, see
     * CashCloseServiceImpl#isReviewRequired) are both awaiting a manager's decision.
     * A cash close that already has a final decision (APPROVED/REJECTED) or is VOIDED
     * cannot be reviewed again.
     */
    private CashClose requireReviewable(Long cashCloseId) {
        CashClose cashClose = findCashClose(cashCloseId);
        CashCloseStatus status = cashClose.getStatus();
        if (status != CashCloseStatus.SUBMITTED && status != CashCloseStatus.PENDING_REVIEW) {
            throw new BadRequestException("Cash close must be SUBMITTED or PENDING_REVIEW to be reviewed");
        }
        return cashClose;
    }

    private CashClose requireNotVoided(Long cashCloseId) {
        CashClose cashClose = findCashClose(cashCloseId);
        if (cashClose.getStatus() == CashCloseStatus.VOIDED) {
            throw new BadRequestException("Cash close is already voided");
        }
        return cashClose;
    }

    private CashClose findCashClose(Long cashCloseId) {
        return cashCloseRepository.findById(cashCloseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash close not found with id " + cashCloseId));
    }

    private String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new BadRequestException("A rejection note is required");
        }
        return note;
    }

    private User currentReviewer() {
        Long reviewerId = SecurityUtils.currentUserId();
        return userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + reviewerId));
    }

    private ApprovalResponseDTO toResponseDTO(Approval approval) {
        User reviewer = approval.getReviewedBy();
        return ApprovalResponseDTO.builder()
                .id(approval.getId())
                .cashCloseId(approval.getCashClose().getId())
                .action(approval.getAction().name())
                .reason(approval.getNote())
                .approvedByUserId(reviewer.getId())
                .approvedByName(reviewer.getFullName())
                .approvedAt(approval.getReviewedAt())
                .build();
    }
}

package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.ApprovalRequest;
import com.comfy.caseclose.dto.response.ApprovalResponseDTO;

import java.util.List;

public interface ApprovalService {

    void approveCashClose(Long cashCloseId, ApprovalRequest request);

    void rejectCashClose(Long cashCloseId, ApprovalRequest request);

    void voidCashClose(Long cashCloseId, ApprovalRequest request);

    List<ApprovalResponseDTO> getApprovalHistory(Long cashCloseId);
}

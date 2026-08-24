package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.ApprovalRequest;
import com.comfy.caseclose.dto.response.ApprovalResponseDTO;
import com.comfy.caseclose.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-closes/{id}")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Void> approve(@PathVariable Long id, @RequestBody ApprovalRequest request) {
        approvalService.approveCashClose(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Void> reject(@PathVariable Long id, @RequestBody ApprovalRequest request) {
        approvalService.rejectCashClose(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/void")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> voidClose(@PathVariable Long id, @RequestBody ApprovalRequest request) {
        approvalService.voidCashClose(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<ApprovalResponseDTO>> getApprovalHistory(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.getApprovalHistory(id));
    }
}

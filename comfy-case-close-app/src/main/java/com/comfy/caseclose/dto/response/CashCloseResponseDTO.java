package com.comfy.caseclose.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashCloseResponseDTO {
    // Core identifiers
    private Long id;
    private String referenceCode;
    private Long branchId;
    private Long shiftTypeId;
    private Long submittedByUserId;
    private String submittedByName;
    
    // Dates
    private LocalDate businessDate;
    private OffsetDateTime submittedAt;
    
    // Status and risk
    private String status;  // CashCloseStatus (SUBMITTED, PENDING_REVIEW, APPROVED, REJECTED, VOIDED)
    private String riskLevel;  // RiskLevel (LOW, MEDIUM, HIGH, CRITICAL)
    
    // Input cash count
    private Long countedCash;  // VND
    
    // Computed cash fields (all from GAS source line 1330+)
    private Long systemPotBefore;  // Computed from last close
    private Long systemPotAfter;   // Computed: systemPotBefore + countedCash (line 3463)
    private Long cashRemaining;    // Computed: systemPotAfter - totalExpense - tipsAmount (line 1350)
    private Long countedCashTotal; // Computed: sum of denominations
    private Long cashDiff;         // Computed: countedCash - countedCashTotal (line 1348)
    private Long totalExpense;     // Computed: sum of EXPENSE + END_OF_DAY_EXPENSE movements (line 1347)
    private Long endOfDayExpenseAmount;  // Computed: sum of END_OF_DAY_EXPENSE (line 1346)
    private Long tipsAmount;       // Computed: from tips table or movements
    private Long explainedDiff;    // Computed: sum of explanations (line 1391)
    private Long unexplainedDiff;  // Computed: cashDiff - explainedDiff (line 1392)
    
    // Workflow
    private String approvalStatus;  // AUTO_APPROVED, PENDING_MANAGER_REVIEW, APPROVED, REJECTED
    private String managerReviewNote;  // From most recent approval
    
    // Notes
    private String notes;
    private Boolean isLateSubmission;  // Computed by service layer
    
    // Relationships (nested)
    private List<CashMovementResponseDTO> movements;
    private List<CashDiffExplanationResponseDTO> explanations;
    private List<AttachmentResponseDTO> attachments;
    private List<ApprovalResponseDTO> approvals;
}

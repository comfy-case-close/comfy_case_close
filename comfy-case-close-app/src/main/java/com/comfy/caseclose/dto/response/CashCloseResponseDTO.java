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
    private Long id;
    private String referenceCode;
    private Long branchId;
    private Long shiftTypeId;
    private Long submittedByUserId;
    private String submittedByName;

    private LocalDate businessDate;
    private OffsetDateTime submittedAt;

    private String status;
    private String riskLevel;

    private Long countedCash;
    private Long posExpectedCash;   // VND — what the POS said the drawer should hold
    private Long withdrawalAmount;  // VND — pulled out of the drawer at close
    private Long systemPotBefore;
    private Long systemPotAfter;
    private Long cashRemaining;
    private Long countedCashTotal;
    private Long cashDiff;
    private Long totalExpense;
    private Long endOfDayExpenseAmount;
    private Long tipsAmount;
    /** Tips kept outside the drawer (tip jar) vs tips merged into the drawer. */
    private Long tipsSeparateAmount;
    private Long tipsInsideDrawerAmount;
    private Long explainedDiff;
    private Long unexplainedDiff;

    private String approvalStatus;
    private String managerReviewNote;

    private String notes;
    private Boolean isLateSubmission;

    private List<CashMovementResponseDTO> movements;
    private List<CashDiffExplanationResponseDTO> explanations;
    private List<AttachmentResponseDTO> attachments;
    private List<ApprovalResponseDTO> approvals;
}

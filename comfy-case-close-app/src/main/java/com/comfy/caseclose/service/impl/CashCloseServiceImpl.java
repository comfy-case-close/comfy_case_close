package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.AttachmentRequest;
import com.comfy.caseclose.dto.request.CashCloseSubmitRequest;
import com.comfy.caseclose.dto.request.CashDenominationRequest;
import com.comfy.caseclose.dto.request.CashDiffExplanationRequest;
import com.comfy.caseclose.dto.request.CashMovementRequest;
import com.comfy.caseclose.dto.request.TipRequest;
import com.comfy.caseclose.dto.response.ApprovalResponseDTO;
import com.comfy.caseclose.dto.response.AttachmentResponseDTO;
import com.comfy.caseclose.dto.response.CarryForwardDTO;
import com.comfy.caseclose.dto.response.CashCloseResponseDTO;
import com.comfy.caseclose.dto.response.CashDenominationResponseDTO;
import com.comfy.caseclose.dto.response.CashDiffExplanationResponseDTO;
import com.comfy.caseclose.dto.response.CashMovementResponseDTO;
import com.comfy.caseclose.dto.response.DaySummaryDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.TipResponseDTO;
import com.comfy.caseclose.entity.Approval;
import com.comfy.caseclose.entity.Attachment;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.CashDenomination;
import com.comfy.caseclose.entity.CashDiffExplanation;
import com.comfy.caseclose.entity.CashMovement;
import com.comfy.caseclose.entity.ShiftType;
import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.ApprovalRepository;
import com.comfy.caseclose.repository.AttachmentRepository;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashDenominationRepository;
import com.comfy.caseclose.repository.CashDiffExplanationRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.ShiftTypeRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.CashCloseService;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.PaginationUtils;
import com.comfy.caseclose.utils.enums.AttachmentType;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.DiffDirection;
import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashCloseServiceImpl implements CashCloseService {

    private final CashCloseRepository cashCloseRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashDiffExplanationRepository cashDiffExplanationRepository;
    private final CashDenominationRepository cashDenominationRepository;
    private final TipRepository tipRepository;
    private final AttachmentRepository attachmentRepository;
    private final ApprovalRepository approvalRepository;
    private final BranchRepository branchRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CashCloseResponseDTO submitCashClose(CashCloseSubmitRequest request) {
        Branch branch = resolveBranch(request.getBranchId());
        ShiftType shiftType = resolveShiftType(request.getShiftTypeId());
        User submittedBy = resolveCurrentUser();
        requireNoActiveClose(branch.getId(), request.getBusinessDate(), shiftType.getId());

        requireDenominationsMatchCountedCash(request);

        CashClose cashClose = cashCloseRepository.save(buildCashClose(request, branch, shiftType, submittedBy));
        persistDenominations(request.getDenominations(), cashClose);
        persistMovements(request.getMovements(), cashClose);
        persistExplanations(request.getExplanations(), cashClose);
        persistTip(request.getTips(), cashClose);
        persistAttachments(request.getAttachments(), cashClose);

        applyRiskAndStatus(cashClose);
        return toResponseDTO(cashClose);
    }

    @Override
    @Transactional(readOnly = true)
    public CashCloseResponseDTO getCashCloseById(Long id) {
        return toResponseDTO(findCashClose(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashCloseResponseDTO> listCashCloses(
            Long branchId, LocalDate businessDate, String status, Pageable pageable) {

        CashCloseStatus statusFilter = parseStatus(status);
        return PaginationUtils.toPagedResponse(
                cashCloseRepository.findAll(buildListFilter(branchId, businessDate, statusFilter), pageable),
                this::toResponseDTO);
    }

    private CashCloseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CashCloseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown cash close status: " + status);
        }
    }

    /**
     * Built as a Specification rather than a JPQL "(:param IS NULL OR ...)" query:
     * on Postgres, a bind parameter used only in an IS NULL check has no other
     * usage to pin its type, and the driver fails with "could not determine data
     * type of parameter" for server-side prepared statements. A Specification
     * simply omits a predicate for an absent filter instead of asking Postgres to
     * reason about it.
     */
    private Specification<CashClose> buildListFilter(Long branchId, LocalDate businessDate, CashCloseStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branch").get("id"), branchId));
            }
            if (businessDate != null) {
                predicates.add(cb.equal(root.get("businessDate"), businessDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), CashCloseStatus.VOIDED));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashDenominationResponseDTO> getDenominations(Long cashCloseId) {
        return cashDenominationRepository.findByCashCloseId(cashCloseId).stream()
                .map(d -> CashDenominationResponseDTO.builder()
                        .id(d.getId())
                        .cashCloseId(d.getCashClose().getId())
                        .denominationValue(d.getDenominationValue())
                        .quantity(d.getQuantity())
                        .lineTotal(d.getDenominationValue() * d.getQuantity())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TipResponseDTO> getTips(Long cashCloseId) {
        return tipRepository.findByCashCloseId(cashCloseId).stream()
                .map(t -> TipResponseDTO.builder()
                        .id(t.getId())
                        .cashCloseId(t.getCashClose().getId())
                        .amount(t.getAmount())
                        .isInsideCashDrawer(t.getIsInsideCashDrawer())
                        .note(t.getNote())
                        .createdAt(t.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DaySummaryDTO getDaySummary(Long cashCloseId) {
        CashClose current = findCashClose(cashCloseId);
        Long branchId = current.getBranch().getId();
        LocalDate businessDate = current.getBusinessDate();
        short currentOrder = current.getShiftType().getSortOrder();

        List<CashClose> dayCloses = activeSameDayCloses(branchId, businessDate);
        List<CashClose> priorCloses = dayCloses.stream()
                .filter(cc -> cc.getShiftType().getSortOrder() < currentOrder)
                .toList();

        Computed currentTotals = computeTotals(current);
        long priorExplainedDiff = priorCloses.stream().mapToLong(cc -> computeTotals(cc).explainedDiff()).sum();

        boolean isEvening = "EVENING_CLOSE".equals(current.getShiftType().getShiftTypeCode());
        long dayCashDiff = currentTotals.cashDiff();
        long dayExplainedDiff = isEvening ? priorExplainedDiff + currentTotals.explainedDiff() : currentTotals.explainedDiff();
        long dayUnexplainedDiff = isEvening ? dayCashDiff - dayExplainedDiff : currentTotals.unexplainedDiff();

        return DaySummaryDTO.builder()
                .businessDate(businessDate)
                .branchId(branchId)
                .branchCode(current.getBranch().getBranchCode())
                .shiftCount(dayCloses.size())
                .closeIds(dayCloses.stream().map(CashClose::getId).toList())
                .shiftNames(dayCloses.stream().map(cc -> cc.getShiftType().getShiftName()).toList())
                .currentCashCloseId(current.getId())
                .currentShiftTypeCode(current.getShiftType().getShiftTypeCode())
                .priorCloseIds(priorCloses.stream().map(CashClose::getId).toList())
                .dayTotalExpense(dayCloses.stream().mapToLong(cc -> computeTotals(cc).totalExpense() + computeTotals(cc).endOfDayExpense()).sum())
                .dayExpenseAffectingDiff(dayCloses.stream().mapToLong(cc -> computeTotals(cc).totalExpense()).sum())
                .dayEndOfDayExpense(dayCloses.stream().mapToLong(cc -> computeTotals(cc).endOfDayExpense()).sum())
                .dayTipsAmount(dayCloses.stream().mapToLong(cc -> computeTotals(cc).tipsAmount()).sum())
                .dayCashDiff(dayCashDiff)
                .priorExplainedDiff(priorExplainedDiff)
                .currentExplainedDiff(currentTotals.explainedDiff())
                .dayExplainedDiff(dayExplainedDiff)
                .dayUnexplainedDiff(dayUnexplainedDiff)
                .effectiveUnexplainedDiff(dayUnexplainedDiff)
                .appliedPriorCarry(isEvening && !priorCloses.isEmpty())
                .carryMode(isEvening ? "DAY_SNAPSHOT" : "CURRENT_SHIFT_ONLY")
                .dayBillIssueAmount(dayCloses.stream().mapToLong(cc -> billIssueAmount(cc.getId())).sum())
                .dayWithdrawal(dayCloses.stream().mapToLong(CashClose::getWithdrawalAmount).sum())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CarryForwardDTO getCarryForward(Long branchId, LocalDate businessDate, Long shiftTypeId) {
        Branch branch = resolveBranch(branchId);
        ShiftType shiftType = resolveShiftType(shiftTypeId);
        short currentOrder = shiftType.getSortOrder();

        List<CashClose> priorCloses = activeSameDayCloses(branchId, businessDate).stream()
                .filter(cc -> cc.getShiftType().getSortOrder() < currentOrder)
                .toList();

        Map<Long, Computed> totalsByClose = priorCloses.stream()
                .collect(Collectors.toMap(CashClose::getId, this::computeTotals));

        return CarryForwardDTO.builder()
                .branchId(branch.getId())
                .branchCode(branch.getBranchCode())
                .businessDate(businessDate)
                .shiftTypeId(shiftType.getId())
                .shiftTypeCode(shiftType.getShiftTypeCode())
                .priorShiftCount(priorCloses.size())
                .priorCloseIds(priorCloses.stream().map(CashClose::getId).toList())
                .priorShiftNames(priorCloses.stream().map(cc -> cc.getShiftType().getShiftName()).toList())
                .priorCashDiff(priorCloses.stream().mapToLong(cc -> totalsByClose.get(cc.getId()).cashDiff()).sum())
                .priorExplainedDiff(priorCloses.stream().mapToLong(cc -> totalsByClose.get(cc.getId()).explainedDiff()).sum())
                .priorUnexplainedDiff(priorCloses.stream().mapToLong(cc -> totalsByClose.get(cc.getId()).unexplainedDiff()).sum())
                .priorExpense(priorCloses.stream().mapToLong(cc -> totalsByClose.get(cc.getId()).totalExpense()).sum())
                .priorTips(priorCloses.stream().mapToLong(cc -> totalsByClose.get(cc.getId()).tipsAmount()).sum())
                .priorItems(priorCloses.stream().map(cc -> toPriorItem(cc, totalsByClose.get(cc.getId()))).toList())
                .priorExpenseItems(toPriorExpenseItems(priorCloses))
                .build();
    }

    private CarryForwardDTO.PriorItem toPriorItem(CashClose cc, Computed totals) {
        return CarryForwardDTO.PriorItem.builder()
                .cashCloseId(cc.getId())
                .shiftTypeCode(cc.getShiftType().getShiftTypeCode())
                .shiftName(cc.getShiftType().getShiftName())
                .submittedAt(cc.getSubmittedAt())
                .cashDiff(totals.cashDiff())
                .explainedDiff(totals.explainedDiff())
                .unexplainedDiff(totals.unexplainedDiff())
                .totalExpense(totals.totalExpense())
                .tipsAmount(totals.tipsAmount())
                .build();
    }

    private List<CarryForwardDTO.PriorExpenseItem> toPriorExpenseItems(List<CashClose> priorCloses) {
        if (priorCloses.isEmpty()) {
            return List.of();
        }
        Map<Long, String> shiftNameByClose = priorCloses.stream()
                .collect(Collectors.toMap(CashClose::getId, cc -> cc.getShiftType().getShiftName()));
        List<Long> ids = priorCloses.stream().map(CashClose::getId).toList();
        return cashMovementRepository.findByCashCloseIdIn(ids).stream()
                .map(m -> CarryForwardDTO.PriorExpenseItem.builder()
                        .cashCloseId(m.getCashClose().getId())
                        .shiftName(shiftNameByClose.get(m.getCashClose().getId()))
                        .movementType(m.getMovementType().name())
                        .category(m.getCategory().name())
                        .amount(m.getAmount())
                        .affectsDiff(m.getMovementType() == MovementType.EXPENSE)
                        .description(m.getDescription())
                        .build())
                .toList();
    }

    private List<CashClose> activeSameDayCloses(Long branchId, LocalDate businessDate) {
        return cashCloseRepository.findActiveByBranchAndDate(branchId, businessDate).stream()
                .filter(cc -> cc.getStatus() != CashCloseStatus.REJECTED)
                .sorted(Comparator.comparing((CashClose cc) -> cc.getShiftType().getSortOrder())
                        .thenComparing(CashClose::getSubmittedAt))
                .toList();
    }

    private long billIssueAmount(Long cashCloseId) {
        return cashDiffExplanationRepository.findByCashCloseId(cashCloseId).stream()
                .filter(e -> e.getReasonType() == DiffReasonType.UNPAID_BILL)
                .mapToLong(e -> Math.abs(e.getSignedAmount()))
                .sum();
    }

    // ----- submit helpers -------------------------------------------------------------------------

    private CashClose buildCashClose(CashCloseSubmitRequest request, Branch branch, ShiftType shiftType, User submittedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        CashClose cashClose = new CashClose();
        cashClose.setReferenceCode(generateReferenceCode());
        cashClose.setBranch(branch);
        cashClose.setShiftType(shiftType);
        cashClose.setSubmittedBy(submittedBy);
        cashClose.setBusinessDate(request.getBusinessDate());
        cashClose.setPosExpectedCash(request.getPosExpectedCash());
        cashClose.setCountedCash(request.getCountedCash());
        cashClose.setWithdrawalAmount(request.getWithdrawalAmount());
        cashClose.setNote(InputNormalizer.text(request.getNote()));
        cashClose.setStatus(CashCloseStatus.SUBMITTED);
        cashClose.setRiskLevel(RiskLevel.LOW);
        cashClose.setSubmittedAt(now);
        cashClose.setUpdatedAt(now);
        return cashClose;
    }

    private void persistMovements(List<CashMovementRequest> requests, CashClose cashClose) {
        if (requests == null) {
            return;
        }
        for (CashMovementRequest req : requests) {
            CashMovement movement = new CashMovement();
            movement.setCashClose(cashClose);
            MovementType movementType = MovementType.valueOf(req.getType());
            movement.setMovementType(movementType);
            movement.setCategory(MovementCategory.valueOf(req.getCategory()));
            movement.setAmount(req.getAmount());
            movement.setDescription(InputNormalizer.text(req.getDescription()));
            cashMovementRepository.save(movement);

            // database.md cash_movements "Backend rule": an EXPENSE was paid out of the
            // drawer, so it already explains part of the POS-vs-counted diff — auto-file
            // it as an explanation (same pattern as the TIPS_IN_CASH_DRAWER auto-explain
            // in persistTip below) instead of leaving it to inflate unexplainedDiff until
            // someone re-enters it by hand. `reason` is the shift lead's own pick from the
            // expense row (ExpenseList.tsx), not derived from category, since the two are
            // independent required fields on the same request. END_OF_DAY_EXPENSE is
            // untouched: that cash is still physically in the drawer, so it has not
            // created a diff yet.
            if (movementType == MovementType.EXPENSE) {
                CashDiffExplanation explanation = new CashDiffExplanation();
                explanation.setCashClose(cashClose);
                explanation.setReasonType(DiffReasonType.valueOf(req.getReason()));
                explanation.setDirection(DiffDirection.SHORTAGE);
                explanation.setSignedAmount(req.getAmount());
                cashDiffExplanationRepository.save(explanation);
            }
        }
    }

    private void persistExplanations(List<CashDiffExplanationRequest> requests, CashClose cashClose) {
        if (requests == null) {
            return;
        }
        for (CashDiffExplanationRequest req : requests) {
            CashDiffExplanation explanation = new CashDiffExplanation();
            explanation.setCashClose(cashClose);
            explanation.setReasonType(DiffReasonType.valueOf(req.getReason()));
            explanation.setSignedAmount(req.getSignedAmount());
            explanation.setDirection(req.getSignedAmount() >= 0 ? DiffDirection.SHORTAGE : DiffDirection.SURPLUS);
            explanation.setNote(InputNormalizer.text(req.getNotes()));
            cashDiffExplanationRepository.save(explanation);
        }
    }

    private void persistAttachments(List<AttachmentRequest> requests, CashClose cashClose) {
        if (requests == null) {
            return;
        }
        for (AttachmentRequest req : requests) {
            Attachment attachment = new Attachment();
            attachment.setCashClose(cashClose);
            attachment.setType(AttachmentType.valueOf(req.getType()));
            attachment.setFileUrl(req.getFileUrl());
            attachment.setFileName(InputNormalizer.text(req.getDescription()));
            attachmentRepository.save(attachment);
        }
    }

    private void persistDenominations(List<CashDenominationRequest> requests, CashClose cashClose) {
        if (requests == null) {
            return;
        }
        for (CashDenominationRequest req : requests) {
            CashDenomination denomination = new CashDenomination();
            denomination.setCashClose(cashClose);
            denomination.setDenominationValue(req.getDenominationValue());
            denomination.setQuantity(req.getQuantity());
            cashDenominationRepository.save(denomination);
        }
    }

    private void persistTip(TipRequest request, CashClose cashClose) {
        if (request == null) {
            return;
        }
        Tip tip = new Tip();
        tip.setCashClose(cashClose);
        tip.setAmount(request.getAmount());
        tip.setIsInsideCashDrawer(Boolean.TRUE.equals(request.getIsInsideCashDrawer()));
        tip.setNote(InputNormalizer.text(request.getNote()));
        tipRepository.save(tip);

        // Tips found inside the drawer inflate the count, so the backend records a SURPLUS explanation
        // that offsets them (database.md backend rule).
        if (Boolean.TRUE.equals(request.getIsInsideCashDrawer()) && request.getAmount() > 0) {
            CashDiffExplanation explanation = new CashDiffExplanation();
            explanation.setCashClose(cashClose);
            explanation.setReasonType(DiffReasonType.TIPS_IN_CASH_DRAWER);
            explanation.setDirection(DiffDirection.SURPLUS);
            explanation.setSignedAmount(-request.getAmount());
            cashDiffExplanationRepository.save(explanation);
        }
    }

    private void requireDenominationsMatchCountedCash(CashCloseSubmitRequest request) {
        List<CashDenominationRequest> denominations = request.getDenominations();
        if (denominations == null || denominations.isEmpty()) {
            return;
        }
        long total = denominations.stream()
                .mapToLong(d -> d.getDenominationValue() * d.getQuantity())
                .sum();
        if (total != request.getCountedCash()) {
            throw new BadRequestException(
                    "Counted cash (" + request.getCountedCash() + ") must equal the denomination total (" + total + ")");
        }
    }

    private void applyRiskAndStatus(CashClose cashClose) {
        Computed computed = computeTotals(cashClose);
        RiskLevel riskLevel = assessRisk(computed);
        cashClose.setRiskLevel(riskLevel);
        cashClose.setStatus(isReviewRequired(riskLevel) ? CashCloseStatus.PENDING_REVIEW : CashCloseStatus.SUBMITTED);
        cashClose.setUpdatedAt(OffsetDateTime.now());
    }

    private boolean isReviewRequired(RiskLevel riskLevel) {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    private void requireNoActiveClose(Long branchId, LocalDate businessDate, Long shiftTypeId) {
        boolean exists = cashCloseRepository.findActiveByBranchAndDate(branchId, businessDate).stream()
                .anyMatch(existing -> Objects.equals(existing.getShiftType().getId(), shiftTypeId));
        if (exists) {
            throw new BadRequestException("An active cash close already exists for this branch, date and shift");
        }
    }

    private Branch resolveBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + branchId));
    }

    private ShiftType resolveShiftType(Long shiftTypeId) {
        return shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift type not found with id " + shiftTypeId));
    }

    private User resolveCurrentUser() {
        Long userId = SecurityUtils.currentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
    }

    private CashClose findCashClose(Long id) {
        return cashCloseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash close not found with id " + id));
    }

    // ----- computed fields ------------------------------------------------------------------------

    private Computed computeTotals(CashClose cashClose) {
        List<CashMovement> movements = cashMovementRepository.findByCashCloseId(cashClose.getId());
        List<CashDiffExplanation> explanations = cashDiffExplanationRepository.findByCashCloseId(cashClose.getId());

        long expense = sumMovements(movements, MovementType.EXPENSE);
        long endOfDayExpense = sumMovements(movements, MovementType.END_OF_DAY_EXPENSE);
        long totalExpense = expense + endOfDayExpense;
        long tipsAmount = tipRepository.findByCashCloseId(cashClose.getId()).stream()
                .mapToLong(Tip::getAmount)
                .sum();
        long explainedDiff = explanations.stream().mapToLong(CashDiffExplanation::getSignedAmount).sum();
        long cashDiff = cashClose.getPosExpectedCash() - cashClose.getCountedCash();
        long cashRemaining = cashClose.getCountedCash() - cashClose.getWithdrawalAmount()
                - tipsAmount - endOfDayExpense;

        return new Computed(totalExpense, endOfDayExpense, tipsAmount, cashDiff, explainedDiff,
                cashDiff - explainedDiff, cashRemaining);
    }

    private long sumMovements(List<CashMovement> movements, MovementType type) {
        return movements.stream()
                .filter(movement -> movement.getMovementType() == type)
                .mapToLong(CashMovement::getAmount)
                .sum();
    }

    private RiskLevel assessRisk(Computed computed) {
        int score = 0;
        long unexplained = Math.abs(computed.unexplainedDiff());
        if (unexplained > 1_000_000) {
            score += 5;
        } else if (unexplained > 500_000) {
            score += 3;
        } else if (unexplained > 100_000) {
            score += 2;
        }
        if (computed.cashRemaining() < 0) {
            score += 4;
        }
        if (score >= 7) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 4) {
            return RiskLevel.HIGH;
        }
        if (score >= 2) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    // ----- mapping --------------------------------------------------------------------------------

    private CashCloseResponseDTO toResponseDTO(CashClose cashClose) {
        List<CashMovement> movements = cashMovementRepository.findByCashCloseId(cashClose.getId());
        List<CashDiffExplanation> explanations = cashDiffExplanationRepository.findByCashCloseId(cashClose.getId());
        List<Attachment> attachments = attachmentRepository.findByCashCloseId(cashClose.getId());
        List<Approval> approvals = approvalRepository.findByCashCloseIdOrderByReviewedAtDesc(cashClose.getId());
        Computed computed = computeTotals(cashClose);

        return CashCloseResponseDTO.builder()
                .id(cashClose.getId())
                .referenceCode(cashClose.getReferenceCode())
                .branchId(cashClose.getBranch().getId())
                .shiftTypeId(cashClose.getShiftType().getId())
                .submittedByUserId(cashClose.getSubmittedBy().getId())
                .submittedByName(cashClose.getSubmittedBy().getFullName())
                .businessDate(cashClose.getBusinessDate())
                .submittedAt(cashClose.getSubmittedAt())
                .status(cashClose.getStatus().name())
                .riskLevel(cashClose.getRiskLevel().name())
                .countedCash(cashClose.getCountedCash())
                .cashDiff(computed.cashDiff())
                .totalExpense(computed.totalExpense())
                .endOfDayExpenseAmount(computed.endOfDayExpense())
                .tipsAmount(computed.tipsAmount())
                .explainedDiff(computed.explainedDiff())
                .unexplainedDiff(computed.unexplainedDiff())
                .cashRemaining(computed.cashRemaining())
                .managerReviewNote(latestReviewNote(approvals))
                .notes(cashClose.getNote())
                .isLateSubmission(cashClose.getIsLate())
                .movements(movements.stream().map(this::toMovementDTO).toList())
                .explanations(explanations.stream().map(this::toExplanationDTO).toList())
                .attachments(attachments.stream().map(this::toAttachmentDTO).toList())
                .approvals(approvals.stream().map(this::toApprovalDTO).toList())
                .build();
    }

    private String latestReviewNote(List<Approval> approvals) {
        return approvals.isEmpty() ? null : approvals.get(0).getNote();
    }

    private CashMovementResponseDTO toMovementDTO(CashMovement movement) {
        return CashMovementResponseDTO.builder()
                .id(movement.getId())
                .cashCloseId(movement.getCashClose().getId())
                .category(movement.getCategory().name())
                .type(movement.getMovementType().name())
                .amount(movement.getAmount())
                .description(movement.getDescription())
                .lineTotal(movement.getAmount())
                .affectsDiff(movement.getMovementType() == MovementType.EXPENSE)
                .build();
    }

    private CashDiffExplanationResponseDTO toExplanationDTO(CashDiffExplanation explanation) {
        return CashDiffExplanationResponseDTO.builder()
                .id(explanation.getId())
                .cashCloseId(explanation.getCashClose().getId())
                .reason(explanation.getReasonType().name())
                .direction(explanation.getDirection().name())
                .signedAmount(explanation.getSignedAmount())
                .amount(Math.abs(explanation.getSignedAmount()))
                .notes(explanation.getNote())
                .build();
    }

    private AttachmentResponseDTO toAttachmentDTO(Attachment attachment) {
        return AttachmentResponseDTO.builder()
                .id(attachment.getId())
                .cashCloseId(attachment.getCashClose().getId())
                .type(attachment.getType().name())
                .fileUrl(attachment.getFileUrl())
                .description(attachment.getFileName())
                .build();
    }

    private ApprovalResponseDTO toApprovalDTO(Approval approval) {
        User reviewer = approval.getReviewedBy();
        return ApprovalResponseDTO.builder()
                .id(approval.getId())
                .cashCloseId(approval.getCashClose().getId())
                .action(approval.getAction().name())
                .reason(approval.getNote())
                .approvedByUserId(reviewer == null ? null : reviewer.getId())
                .approvedByName(reviewer == null ? null : reviewer.getFullName())
                .approvedAt(approval.getReviewedAt())
                .build();
    }

    private String generateReferenceCode() {
        return "CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record Computed(
            long totalExpense,
            long endOfDayExpense,
            long tipsAmount,
            long cashDiff,
            long explainedDiff,
            long unexplainedDiff,
            long cashRemaining) {
    }
}

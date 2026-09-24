package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.AttachmentRequest;
import com.comfy.caseclose.dto.request.CashCloseSubmitRequest;
import com.comfy.caseclose.dto.request.CashCloseUpdateRequest;
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
import com.comfy.caseclose.repository.AppConfigRepository;
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
import com.comfy.caseclose.service.AttachmentService;
import com.comfy.caseclose.service.AttachmentStorageService;
import com.comfy.caseclose.service.CashCloseService;
import com.comfy.caseclose.service.CashCloseSubmittedEvent;
import com.comfy.caseclose.utils.BusinessDates;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.PaginationUtils;
import com.comfy.caseclose.utils.enums.ApprovalAction;
import com.comfy.caseclose.utils.enums.AttachmentType;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.DiffDirection;
import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashCloseServiceImpl implements CashCloseService {

    private static final Logger log = LoggerFactory.getLogger(CashCloseServiceImpl.class);

    private final CashCloseRepository cashCloseRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashDiffExplanationRepository cashDiffExplanationRepository;
    private final CashDenominationRepository cashDenominationRepository;
    private final TipRepository tipRepository;
    private final AttachmentRepository attachmentRepository;
    private final ApprovalRepository approvalRepository;
    private final AppConfigRepository appConfigRepository;
    private final BranchRepository branchRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentService attachmentService;
    private final AttachmentStorageService attachmentStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CashCloseResponseDTO submitCashClose(CashCloseSubmitRequest request) {
        Branch branch = resolveBranch(request.getBranchId());
        ShiftType shiftType = resolveShiftType(request.getShiftTypeId());
        User submittedBy = resolveCurrentUser();
        requireBusinessDateNotInFuture(request.getBusinessDate());
        requireNoActiveClose(branch.getId(), request.getBusinessDate(), shiftType.getId());

        requireValidDenominations(request.getDenominations());
        requireDenominationsMatchCountedCash(request.getDenominations(), request.getCountedCash());
        requireWithdrawalWithinCounted(request.getWithdrawalAmount(), request.getCountedCash());
        requireNoDuplicateExplanations(request.getExplanations());
        requireBillRepaymentProof(request.getExplanations(), request.getMovements(), request.getAttachments());

        CashClose cashClose = cashCloseRepository.save(buildCashClose(request, branch, shiftType, submittedBy));
        persistDenominations(request.getDenominations(), cashClose);
        persistMovements(request.getMovements(), cashClose);
        persistExplanations(request.getExplanations(), cashClose);
        persistTip(request.getTips(), cashClose);
        persistAttachments(request.getAttachments(), cashClose);

        applyRiskAndStatus(cashClose);
        CashCloseResponseDTO response = toResponseDTO(cashClose);
        eventPublisher.publishEvent(new CashCloseSubmittedEvent(
                cashClose.getId(), branch.getId(), cashClose.getReferenceCode(), branch.getBranchCode(),
                shiftType.getShiftTypeCode(), cashClose.getBusinessDate(), submittedBy.getFullName(),
                submittedBy.getEmail(), cashClose.getStatus().name()));
        return response;
    }

    @Override
    @Transactional
    public CashCloseResponseDTO updateCashClose(Long id, CashCloseUpdateRequest request) {
        CashClose cashClose = requireEditable(id);
        Branch branch = resolveBranch(request.getBranchId());
        ShiftType shiftType = resolveShiftType(request.getShiftTypeId());

        requireBusinessDateNotInFuture(request.getBusinessDate());
        requireValidDenominations(request.getDenominations());
        requireDenominationsMatchCountedCash(request.getDenominations(), request.getCountedCash());
        requireWithdrawalWithinCounted(request.getWithdrawalAmount(), request.getCountedCash());
        requireNoDuplicateExplanations(request.getExplanations());
        requireBillRepaymentProof(request.getExplanations(), request.getMovements(), request.getAttachments());
        requireNoActiveClose(branch.getId(), request.getBusinessDate(), shiftType.getId(), id);

        CashCloseStatus previousStatus = cashClose.getStatus();
        EditSnapshot before = snapshot(cashClose);

        replaceDenominations(cashClose, request.getDenominations());
        // Explanations first: replaceExplanations wipes every explanation row for this close, and
        // replaceMovements/replaceTip then re-file their auto-derived ones. The reverse order
        // deleted those auto-derived rows right after creating them.
        replaceExplanations(cashClose, request.getExplanations());
        replaceMovements(cashClose, request.getMovements());
        replaceTip(cashClose, request.getTips());
        replaceAttachments(cashClose, request.getAttachments());

        cashClose.setBranch(branch);
        cashClose.setShiftType(shiftType);
        cashClose.setBusinessDate(request.getBusinessDate());
        cashClose.setPosExpectedCash(request.getPosExpectedCash());
        cashClose.setCountedCash(request.getCountedCash());
        cashClose.setWithdrawalAmount(request.getWithdrawalAmount());
        cashClose.setNote(InputNormalizer.text(request.getNote()));

        // persistMovements/persistTip re-derive denominations/movements/explanations exactly like a
        // fresh submit, then this recomputes risk from the new numbers — same as submitCashClose.
        applyRiskAndStatus(cashClose);
        if (previousStatus == CashCloseStatus.APPROVED || previousStatus == CashCloseStatus.REJECTED) {
            cashClose.setStatus(CashCloseStatus.PENDING_REVIEW);
        }

        // Diffed against the actual post-replace DB rows, not the raw request — persistMovements and
        // persistTip also file their own auto-generated CashDiffExplanation rows (see their
        // javadoc), which never appear in request.getExplanations(); diffing against the request
        // made every edit report a spurious explanations/tips change even when neither was touched.
        String changes = computeChanges(before, snapshot(cashClose));

        logEdit(cashClose, previousStatus, request.getEditReason(), changes);
        return toResponseDTO(cashClose);
    }

    private record EditSnapshot(
            Long branchId,
            Long shiftTypeId,
            LocalDate businessDate,
            Long posExpectedCash,
            Long countedCash,
            Long withdrawalAmount,
            String note,
            Map<Long, Integer> denominations,
            List<Map<String, Object>> movements,
            List<Map<String, Object>> explanations,
            List<Map<String, Object>> tips,
            List<Map<String, Object>> attachments) {
    }

    private EditSnapshot snapshot(CashClose cashClose) {
        Long cashCloseId = cashClose.getId();
        return new EditSnapshot(
                cashClose.getBranch().getId(),
                cashClose.getShiftType().getId(),
                cashClose.getBusinessDate(),
                cashClose.getPosExpectedCash(),
                cashClose.getCountedCash(),
                cashClose.getWithdrawalAmount(),
                cashClose.getNote(),
                cashDenominationRepository.findByCashCloseId(cashCloseId).stream()
                        .collect(Collectors.toMap(CashDenomination::getDenominationValue, CashDenomination::getQuantity)),
                cashMovementRepository.findByCashCloseId(cashCloseId).stream().map(this::canonicalMovement).toList(),
                cashDiffExplanationRepository.findByCashCloseId(cashCloseId).stream()
                        .map(this::canonicalExplanation)
                        .toList(),
                tipRepository.findByCashCloseId(cashCloseId).stream()
                        .filter(tip -> tip.getAmount() != null && tip.getAmount() > 0)
                        .map(this::canonicalTip)
                        .toList(),
                attachmentRepository.findByCashCloseId(cashCloseId).stream().map(this::canonicalAttachment).toList());
    }

    // Denominations diff per value (keyed like uq_cash_denominations_close_value); the other child
    // collections have no stable key, so they diff as a whole old/new list instead.
    private String computeChanges(EditSnapshot before, EditSnapshot after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        putIfChanged(changes, "branchId", before.branchId(), after.branchId());
        putIfChanged(changes, "shiftTypeId", before.shiftTypeId(), after.shiftTypeId());
        putIfChanged(changes, "businessDate", before.businessDate(), after.businessDate());
        putIfChanged(changes, "posExpectedCash", before.posExpectedCash(), after.posExpectedCash());
        putIfChanged(changes, "countedCash", before.countedCash(), after.countedCash());
        putIfChanged(changes, "withdrawalAmount", before.withdrawalAmount(), after.withdrawalAmount());
        putIfChanged(changes, "note", before.note(), after.note());

        Map<String, Object> denominationChanges = diffDenominations(before.denominations(), after.denominations());
        if (!denominationChanges.isEmpty()) {
            changes.put("denominations", denominationChanges);
        }

        // Rows saved before cash_movements.reason_type existed have no reason to compare against.
        List<Map<String, Object>> afterMovements = before.movements().stream().anyMatch(m -> !m.containsKey("reason"))
                ? after.movements().stream().map(this::withoutReason).toList()
                : after.movements();
        putIfListChanged(changes, "movements", before.movements(), afterMovements);
        putIfListChanged(changes, "explanations", before.explanations(), after.explanations());
        putIfListChanged(changes, "tips", before.tips(), after.tips());
        putIfListChanged(changes, "attachments", before.attachments(), after.attachments());

        if (changes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize edit diff", e);
            return null;
        }
    }

    private void putIfChanged(Map<String, Object> changes, String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("old", oldValue);
        entry.put("new", newValue);
        changes.put(field, entry);
    }

    /** Order-insensitive — a replace that deletes and reinserts every row shouldn't read as "changed" just because rows came back in a different order. */
    private void putIfListChanged(
            Map<String, Object> changes, String field, List<Map<String, Object>> oldItems, List<Map<String, Object>> newItems) {
        List<String> oldSorted = oldItems.stream().map(String::valueOf).sorted().toList();
        List<String> newSorted = newItems.stream().map(String::valueOf).sorted().toList();
        if (oldSorted.equals(newSorted)) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("old", oldItems);
        entry.put("new", newItems);
        changes.put(field, entry);
    }

    private Map<String, Object> diffDenominations(Map<Long, Integer> before, Map<Long, Integer> after) {
        Set<Long> allValues = new TreeSet<>();
        allValues.addAll(before.keySet());
        allValues.addAll(after.keySet());

        Map<String, Object> perValue = new LinkedHashMap<>();
        for (Long value : allValues) {
            int oldQty = before.getOrDefault(value, 0);
            int newQty = after.getOrDefault(value, 0);
            if (oldQty != newQty) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("old", oldQty);
                entry.put("new", newQty);
                perValue.put(String.valueOf(value), entry);
            }
        }
        return perValue;
    }

    private Map<String, Object> canonicalMovement(CashMovement movement) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category", movement.getCategory().name());
        map.put("type", movement.getMovementType().name());
        map.put("amount", movement.getAmount());
        if (movement.getReasonType() != null) {
            map.put("reason", movement.getReasonType().name());
        }
        map.put("description", movement.getDescription());
        return map;
    }

    private Map<String, Object> withoutReason(Map<String, Object> movement) {
        Map<String, Object> copy = new LinkedHashMap<>(movement);
        copy.remove("reason");
        return copy;
    }

    private Map<String, Object> canonicalExplanation(CashDiffExplanation explanation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reason", explanation.getReasonType().name());
        map.put("signedAmount", explanation.getSignedAmount());
        map.put("note", explanation.getNote());
        return map;
    }

    private Map<String, Object> canonicalTip(Tip tip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("amount", tip.getAmount());
        map.put("isInsideCashDrawer", tip.getIsInsideCashDrawer());
        map.put("note", tip.getNote());
        return map;
    }

    private Map<String, Object> canonicalAttachment(Attachment attachment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", attachment.getType().name());
        map.put("fileUrl", attachment.getFileUrl());
        map.put("fileName", attachment.getFileName());
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public CashCloseResponseDTO getCashCloseById(Long id) {
        return toResponseDTO(findCashClose(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashCloseResponseDTO> listCashCloses(
            Long branchId,
            Long shiftTypeId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            Pageable pageable) {

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }
        BusinessDates.requireNotInFuture(fromDate, "fromDate");
        BusinessDates.requireNotInFuture(toDate, "toDate");

        CashCloseStatus statusFilter = parseStatus(status);
        Specification<CashClose> filter =
                buildListFilter(branchId, shiftTypeId, fromDate, toDate, statusFilter);
        return PaginationUtils.toPagedResponse(
                cashCloseRepository.findAll(filter, pageable), this::toResponseDTO);
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
    private Specification<CashClose> buildListFilter(
            Long branchId,
            Long shiftTypeId,
            LocalDate fromDate,
            LocalDate toDate,
            CashCloseStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branch").get("id"), branchId));
            }
            if (shiftTypeId != null) {
                predicates.add(cb.equal(root.get("shiftType").get("id"), shiftTypeId));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("businessDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("businessDate"), toDate));
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
                .dayTotalExpense(dayCloses.stream().mapToLong(cc -> computeTotals(cc).totalExpense()).sum())
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
        BusinessDates.requireNotInFuture(businessDate, "Business date");
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
            MovementType movementType = MovementType.valueOf(req.getType());

            DiffReasonType reasonType = DiffReasonType.valueOf(req.getReason());

            CashMovement movement = new CashMovement();
            movement.setCashClose(cashClose);
            movement.setMovementType(movementType);
            movement.setCategory(MovementCategory.valueOf(req.getCategory()));
            movement.setAmount(req.getAmount());
            movement.setReasonType(reasonType);
            movement.setDescription(InputNormalizer.text(req.getDescription()));
            cashMovementRepository.save(movement);

            if (movementType == MovementType.EXPENSE) {
                CashDiffExplanation explanation = new CashDiffExplanation();
                explanation.setCashClose(cashClose);
                explanation.setReasonType(reasonType);
                explanation.setDirection(DiffDirection.SHORTAGE);
                explanation.setSignedAmount(req.getAmount());
                explanation.setNote(InputNormalizer.text(req.getDescription()));
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
            // Prefer the real Drive file name from POST /attachments/upload; fall back to the
            // free-text description for a hand-typed fileUrl, since file_name is the only text
            // column this table has (database.md dropped the separate description/drive-file-id
            // columns during the schema simplification).
            String fileName = req.getFileName() != null ? req.getFileName() : req.getDescription();
            attachment.setFileName(InputNormalizer.text(fileName));
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
            denomination.setQuantity(req.quantityAsInt());
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

        // Separate tips (the default) are counted outside the drawer and touch nothing here. A tip
        // merged into the drawer (no change to hand back, so it stays in the till) inflates the
        // count, so the backend records a SURPLUS explanation that offsets it (database.md backend rule).
        if (Boolean.TRUE.equals(request.getIsInsideCashDrawer()) && request.getAmount() > 0) {
            CashDiffExplanation explanation = new CashDiffExplanation();
            explanation.setCashClose(cashClose);
            explanation.setReasonType(DiffReasonType.TIPS_IN_CASH_DRAWER);
            explanation.setDirection(DiffDirection.SURPLUS);
            explanation.setSignedAmount(-request.getAmount());
            cashDiffExplanationRepository.save(explanation);
        }
    }

    private void requireDenominationsMatchCountedCash(List<CashDenominationRequest> denominations, Long countedCash) {
        if (denominations == null || denominations.isEmpty()) {
            return;
        }
        long total = denominations.stream()
                .mapToLong(d -> d.getDenominationValue() * d.quantityAsInt())
                .sum();
        if (total != countedCash) {
            throw new BadRequestException(
                    "Counted cash (" + countedCash + ") must equal the denomination total (" + total + ")");
        }
    }

    /**
     * A shift can't be closed for a day that hasn't happened yet — a future date would file the
     * close under a report period nobody has reached and dodge same-day carry-forward. Compared
     * against "today" in Vietnam time, so a shift lead closing just after midnight is judged by the
     * same calendar day the branch is actually on.
     */
    private void requireBusinessDateNotInFuture(LocalDate businessDate) {
        BusinessDates.requireNotInFuture(businessDate, "Business date");
    }

    /**
     * Notes are physical objects: a count is a whole, non-negative number and each note value is
     * counted on exactly one row. Bean validation on {@link CashDenominationRequest} already rejects a
     * negative or fractional quantity on the wire; this repeats the whole-number check so the service
     * never depends on the controller having validated (quantityAsInt would otherwise blow up), and
     * adds the cross-row rule bean validation can't express — a repeated note value would otherwise
     * only surface later as a database unique-constraint 409.
     */
    private void requireValidDenominations(List<CashDenominationRequest> denominations) {
        if (denominations == null) {
            return;
        }
        Set<Long> seenValues = new HashSet<>();
        for (CashDenominationRequest row : denominations) {
            if (row.getQuantity().signum() < 0) {
                throw new BadRequestException(
                        "Quantity for denomination " + row.getDenominationValue() + " cannot be negative");
            }
            if (row.getQuantity().stripTrailingZeros().scale() > 0) {
                throw new BadRequestException(
                        "Quantity for denomination " + row.getDenominationValue() + " must be a whole number of notes");
            }
            if (!seenValues.add(row.getDenominationValue())) {
                throw new BadRequestException(
                        "Denomination " + row.getDenominationValue() + " is listed more than once");
            }
        }
    }

    /**
     * The same variance explained twice — an identical (reason, amount, note) row entered again, or a
     * hand-typed TIPS_IN_CASH_DRAWER line on top of the one {@link #persistTip} files by itself when
     * the tip is merged into the drawer. Either double-counts and hides real unexplained cash.
     * The other double-explanation — explaining more than the diff actually is, e.g. an expense that
     * already auto-explains the shortage plus a manual line for the same money — depends on the
     * persisted totals and is caught by {@link #requireNotOverExplained}.
     */
    private void requireNoDuplicateExplanations(List<CashDiffExplanationRequest> explanations) {
        if (explanations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (CashDiffExplanationRequest explanation : explanations) {
            if (DiffReasonType.valueOf(explanation.getReason()) == DiffReasonType.TIPS_IN_CASH_DRAWER) {
                throw new BadRequestException(
                        "TIPS_IN_CASH_DRAWER is filed automatically when tips are merged into the drawer — "
                                + "do not add it as a separate explanation");
            }
            String key = explanation.getReason() + "|" + explanation.getSignedAmount() + "|"
                    + Objects.toString(InputNormalizer.text(explanation.getNotes()), "");
            if (!seen.add(key)) {
                throw new BadRequestException(
                        "Duplicate explanation: " + explanation.getReason() + " " + explanation.getSignedAmount()
                                + " is listed more than once");
            }
        }
    }

    /**
     * A shortage explained as an unpaid ("forgotten") bill has to come with the photo proving it was
     * repaid — REQUIRE_UNPAID_BILL_REPAYMENT in app_config (default on) was configurable but never
     * enforced anywhere, so a close could be submitted with no proof at all. Applies whether the
     * UNPAID_BILL reason comes from a step-8 explanation or from an expense row (which auto-files one).
     * Attachment rows already require a non-blank fileUrl, so "has an attachment of that type" is
     * enough — a failed upload never reaches the server with a URL.
     */
    private void requireBillRepaymentProof(
            List<CashDiffExplanationRequest> explanations,
            List<CashMovementRequest> movements,
            List<AttachmentRequest> attachments) {
        boolean hasBillIssue =
                (explanations != null && explanations.stream()
                        .anyMatch(e -> DiffReasonType.valueOf(e.getReason()) == DiffReasonType.UNPAID_BILL))
                || (movements != null && movements.stream()
                        .anyMatch(m -> MovementType.valueOf(m.getType()) == MovementType.EXPENSE
                                && DiffReasonType.valueOf(m.getReason()) == DiffReasonType.UNPAID_BILL));
        if (!hasBillIssue || !billRepaymentProofRequired()) {
            return;
        }
        boolean hasProof = attachments != null && attachments.stream()
                .anyMatch(a -> AttachmentType.valueOf(a.getType()) == AttachmentType.UNPAID_BILL_REPAYMENT_PROOF
                        && a.getFileUrl() != null && !a.getFileUrl().isBlank());
        if (!hasProof) {
            throw new BadRequestException(
                    "A repayment proof photo (UNPAID_BILL_REPAYMENT_PROOF) is required when a variance is "
                            + "explained as an unpaid bill");
        }
    }

    /** Defaults to required when the singleton config row is missing, matching the column default. */
    private boolean billRepaymentProofRequired() {
        return appConfigRepository.findById((short) 1)
                .map(config -> Boolean.TRUE.equals(config.getRequireUnpaidBillRepayment()))
                .orElse(true);
    }

    /**
     * Mirrors the frontend's own blocking rule (cash-close-math.ts validateSubmission,
     * 'withdrawalExceedsCounted') at the one place that actually matters: the server never let
     * the client's UI be the only thing standing between a shift lead and an impossible
     * withdrawal — a request built outside the form (or a future bug in it) must be rejected here
     * too, not just flagged for risk after the fact.
     */
    private void requireWithdrawalWithinCounted(Long withdrawalAmount, Long countedCash) {
        if (withdrawalAmount > countedCash) {
            throw new BadRequestException(
                    "Withdrawal amount (" + withdrawalAmount
                            + ") cannot exceed counted cash (" + countedCash + ")");
        }
    }

    private void applyRiskAndStatus(CashClose cashClose) {
        Computed computed = computeTotals(cashClose);
        requireCashRemainingNotNegative(computed);
        requireNotOverExplained(computed);
        RiskLevel riskLevel = assessRisk(computed);
        cashClose.setRiskLevel(riskLevel);
        cashClose.setStatus(isReviewRequired(riskLevel) ? CashCloseStatus.PENDING_REVIEW : CashCloseStatus.SUBMITTED);
        cashClose.setUpdatedAt(OffsetDateTime.now());
    }

    /**
     * Mirrors the frontend's blocking rule ('cashRemainingNegative'). Previously this only added
     * +4 to the risk score — enough to reach PENDING_REVIEW on its own, but not to actually reject
     * a physically impossible till, and not enough at all if some other risk factor pushed the
     * score down (e.g. a config change to the thresholds). Runs after denominations/movements/
     * explanations/tips are already persisted in this @Transactional method, so throwing here
     * rolls back the whole submission cleanly rather than leaving a half-written close.
     */
    private void requireCashRemainingNotNegative(Computed computed) {
        if (computed.cashRemaining() < 0) {
            throw new BadRequestException(
                    "Cash remaining after withdrawal and end-of-day spend cannot be negative ("
                            + computed.cashRemaining() + "). Check the withdrawal amount and end-of-day spend.");
        }
    }

    /**
     * Explanations may cover the diff but never exceed it: once auto-filed expense/tip explanations
     * are counted, the explained total must not overshoot the POS-vs-counted diff (and a close with no
     * diff at all has nothing to explain). An overshoot is the same money explained twice — e.g. an
     * in-shift expense (auto-explains its own amount) plus a hand-typed line for that same expense —
     * and it would cancel out a genuine shortage elsewhere in the shift. Net-based on purpose, so
     * deliberately offsetting lines (a shortage explained one way, a surplus another) still pass.
     * Throws inside the submit/update transaction, so nothing is left half-written.
     */
    private void requireNotOverExplained(Computed computed) {
        long cashDiff = computed.cashDiff();
        long unexplained = computed.unexplainedDiff();
        boolean overExplained = cashDiff == 0 ? unexplained != 0 : Long.signum(unexplained) == -Long.signum(cashDiff);
        if (overExplained) {
            throw new BadRequestException(
                    "Explanations (" + computed.explainedDiff() + ") exceed the cash difference (" + cashDiff
                            + "). The same amount may have been explained twice — check expenses and step-8 explanations.");
        }
    }

    private boolean isReviewRequired(RiskLevel riskLevel) {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    private void requireNoActiveClose(Long branchId, LocalDate businessDate, Long shiftTypeId) {
        requireNoActiveClose(branchId, businessDate, shiftTypeId, null);
    }

    /**
     * {@code excludeCashCloseId} lets an edit that doesn't change branch/date/shift pass this check
     * against itself — without it, correcting any other field on a close would spuriously collide
     * with "an active close already exists" naming the very row being edited.
     */
    private void requireNoActiveClose(Long branchId, LocalDate businessDate, Long shiftTypeId, Long excludeCashCloseId) {
        boolean exists = cashCloseRepository.findActiveByBranchAndDate(branchId, businessDate).stream()
                .filter(existing -> !Objects.equals(existing.getId(), excludeCashCloseId))
                .anyMatch(existing -> Objects.equals(existing.getShiftType().getId(), shiftTypeId));
        if (exists) {
            throw new BadRequestException("An active cash close already exists for this branch, date and shift");
        }
    }

    private CashClose requireEditable(Long id) {
        CashClose cashClose = findCashClose(id);
        if (cashClose.getStatus() == CashCloseStatus.VOIDED) {
            throw new BadRequestException("A voided cash close cannot be edited");
        }
        return cashClose;
    }

    // ----- update helpers --------------------------------------------------------------------------

    private void replaceDenominations(CashClose cashClose, List<CashDenominationRequest> requests) {
        cashDenominationRepository.deleteByCashCloseId(cashClose.getId());
        persistDenominations(requests, cashClose);
    }

    private void replaceMovements(CashClose cashClose, List<CashMovementRequest> requests) {
        // Must run after replaceExplanations — persistMovements files an auto explanation per EXPENSE.
        cashMovementRepository.deleteByCashCloseId(cashClose.getId());
        persistMovements(requests, cashClose);
    }

    private void replaceExplanations(CashClose cashClose, List<CashDiffExplanationRequest> requests) {
        cashDiffExplanationRepository.deleteByCashCloseId(cashClose.getId());
        persistExplanations(requests, cashClose);
    }

    private void replaceTip(CashClose cashClose, TipRequest request) {
        tipRepository.deleteByCashCloseId(cashClose.getId());
        persistTip(request, cashClose);
    }

    /**
     * Goes through {@link AttachmentService#deleteAttachment} — not a bulk repository delete — so
     * the underlying GCS object for every removed attachment is actually cleaned up (deferred to
     * after this transaction commits; see that method's own doc comment), the same as deleting a
     * single attachment anywhere else in the app. Re-sent attachments the shift lead didn't touch
     * still get deleted-and-recreated here; this is a correction flow an admin uses occasionally,
     * not a hot path, so the simplicity of one replace strategy for every child collection wins over
     * optimizing away a same-file re-upload.
     */
    private void replaceAttachments(CashClose cashClose, List<AttachmentRequest> requests) {
        for (Attachment existing : attachmentRepository.findByCashCloseId(cashClose.getId())) {
            attachmentService.deleteAttachment(existing.getId());
        }
        persistAttachments(requests, cashClose);
    }

    private void logEdit(CashClose cashClose, CashCloseStatus previousStatus, String editReason, String changes) {
        Approval approval = new Approval();
        approval.setCashClose(cashClose);
        approval.setAction(ApprovalAction.EDIT);
        approval.setNote(InputNormalizer.text(editReason));
        approval.setReviewedBy(resolveCurrentUser());
        approval.setReviewedAt(OffsetDateTime.now());
        approval.setOldStatus(previousStatus);
        approval.setNewStatus(cashClose.getStatus());
        approval.setChanges(changes);
        approvalRepository.save(approval);
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
        List<Tip> tips = tipRepository.findByCashCloseId(cashClose.getId());
        long tipsInsideDrawer = tips.stream()
                .filter(tip -> Boolean.TRUE.equals(tip.getIsInsideCashDrawer()))
                .mapToLong(Tip::getAmount)
                .sum();
        long tipsAmount = tips.stream().mapToLong(Tip::getAmount).sum();
        long explainedDiff = explanations.stream().mapToLong(CashDiffExplanation::getSignedAmount).sum();
        long cashDiff = cashClose.getPosExpectedCash() - cashClose.getCountedCash();
        // Tips never come out of counted cash: a separate tip is counted outside the drawer (the tip
        // jar) and was never part of countedCash, and a tip merged into the drawer is deliberately
        // kept there as change float — so neither leaves the till at close.
        long cashRemaining = cashClose.getCountedCash() - cashClose.getWithdrawalAmount() - endOfDayExpense;

        return new Computed(totalExpense, endOfDayExpense, tipsAmount, tipsInsideDrawer, cashDiff, explainedDiff,
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
                .posExpectedCash(cashClose.getPosExpectedCash())
                .withdrawalAmount(cashClose.getWithdrawalAmount())
                .cashDiff(computed.cashDiff())
                .totalExpense(computed.totalExpense())
                .endOfDayExpenseAmount(computed.endOfDayExpense())
                .tipsAmount(computed.tipsAmount())
                .tipsSeparateAmount(computed.tipsAmount() - computed.tipsInsideDrawer())
                .tipsInsideDrawerAmount(computed.tipsInsideDrawer())
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
        return approvals.isEmpty() ? null : approvals.getFirst().getNote();
    }

    private CashMovementResponseDTO toMovementDTO(CashMovement movement) {
        return CashMovementResponseDTO.builder()
                .id(movement.getId())
                .cashCloseId(movement.getCashClose().getId())
                .category(movement.getCategory().name())
                .type(movement.getMovementType().name())
                .amount(movement.getAmount())
                .reason(movement.getReasonType() == null ? null : movement.getReasonType().name())
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
                .viewUrl(attachmentStorageService.viewUrlFor(attachment.getFileUrl()))
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
                .changes(approval.getChanges())
                .build();
    }

    private String generateReferenceCode() {
        return "CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record Computed(
            long totalExpense,
            long endOfDayExpense,
            long tipsAmount,
            long tipsInsideDrawer,
            long cashDiff,
            long explainedDiff,
            long unexplainedDiff,
            long cashRemaining) {
    }
}

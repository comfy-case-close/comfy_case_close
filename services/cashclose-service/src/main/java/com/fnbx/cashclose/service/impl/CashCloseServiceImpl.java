package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.response.CashCloseResponse;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.CloseDecisionResponse;
import com.fnbx.cashclose.dto.response.MovementDecisionResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseDecision;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.ApprovalAction;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.ExpectedCashSource;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseDecisionRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashDenominationLineRepository;
import com.fnbx.cashclose.repository.CashMovementDecisionRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.MovementKindRepository;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.cashclose.service.CashCloseView;
import com.fnbx.cashclose.service.ShiftSalesLookup;
import com.fnbx.cashclose.service.rule.CashCloseContext;
import com.fnbx.cashclose.service.rule.RuleRegistry;
import com.fnbx.cashclose.service.rule.ValidationResult;
import com.fnbx.identity.entity.Business;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.enums.EffectType;
import com.fnbx.shared.enums.BusinessType;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.security.AccessPrincipal;
import com.fnbx.shared.enums.UserRole;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import com.fnbx.shared.utils.ValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementation of {@link CashCloseService}.
 *
 * <h2>What is deliberately absent</h2>
 * <ul>
 *   <li><b>No flush/refresh before reading totals.</b> They used to be
 *       trigger-written columns, so the code had to force a round trip. They now
 *       come from a view, and {@code CashCloseCalc} declares {@code @Synchronize},
 *       so Hibernate flushes pending changes before querying.</li>
 *   <li><b>No drawer identity check.</b> {@code cashRemaining} is that expression
 *       now, not a column the app writes and the DB verifies.</li>
 *   <li><b>No riskLevel write.</b> It is derived - see {@link CashCloseView}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashCloseServiceImpl implements CashCloseService {

    private final CashCloseRepository closeRepository;
    private final CashCloseCalcRepository calcRepository;
    private final CashDenominationLineRepository denominationLineRepository;
    private final CashMovementRepository movementRepository;
    private final CashMovementDecisionRepository movementDecisionRepository;
    private final CashCloseDecisionRepository closeDecisionRepository;
    private final MovementKindRepository movementKindRepository;
    private final RuleRegistry ruleRegistry;
    private final ShiftSalesLookup shiftSalesLookup;
    private final CashCloseMapper mapper;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    @Override
    @Transactional
    public CashCloseResponse openDraft(OpenDraftRequest request) {
        TenantContext tenant = TenantContext.current();
        UUID businessId = tenant.businessId();
        AccessPrincipal.current().requireBranch(request.getBranchId());

        boolean exists = closeRepository.existsByBranchIdAndShiftTypeIdAndBusinessDateAndStatusNot(
                request.getBranchId(), request.getShiftTypeId(),
                request.getBusinessDate(), CloseStatus.VOIDED);
        if (exists) {
            throw CashCloseExceptions.closeAlreadyExists();
        }

        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBusinessId(businessId);
        close.setCreatedBy(tenant.userId());
        close.setBranchId(request.getBranchId());
        close.setShiftTypeId(request.getShiftTypeId());
        close.setBusinessDate(request.getBusinessDate());
        close.setCashCloseCode(buildCode(request));
        close.setStatus(CloseStatus.DRAFT);

        shiftSalesLookup
                .findLatest(request.getBranchId(), request.getShiftTypeId(), request.getBusinessDate())
                .ifPresentOrElse(sales -> {
                    close.setPosExpectedCash(sales.getCashSales());
                    close.setExpectedCashSource(ExpectedCashSource.POS_SYNC);
                    close.setPosShiftSalesId(sales.getShiftSalesId());
                }, () -> {
                    close.setPosExpectedCash(BigDecimal.ZERO);
                    close.setExpectedCashSource(ExpectedCashSource.MANUAL);
                });

        return mapper.toResponse(view(closeRepository.save(close)));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashCloseResponse> listCashCloses(CashCloseListFilter filter, Pageable pageable) {
        ValidationUtils.requireValidDateRange(filter.getFromDate(), filter.getToDate());
        CloseStatus status = parseEnum(filter.getStatus(), CloseStatus.class, "cash close status");
        ExpectedCashSource expectedCashSource = parseEnum(
                filter.getExpectedCashSource(), ExpectedCashSource.class, "expected cash source");

        return PaginationUtils.toPagedResponse(
                closeRepository.findAll(cashCloseFilter(filter, status, expectedCashSource), pageable),
                close -> mapper.toResponse(view(close)));
    }

    @Override
    @Transactional(readOnly = true)
    public CashCloseResponse getById(UUID cashCloseId) {
        return mapper.toResponse(view(requireClose(cashCloseId)));
    }

    @Override
    @Transactional
    public CashCloseResponse submit(UUID cashCloseId, String note) {
        CashClose close = requireClose(cashCloseId);
        requireTransition(close, CloseStatus.SUBMITTED);

        if (denominationLineRepository.countByCashCloseId(cashCloseId) == 0) {
            throw CashCloseExceptions.noDenominationCount();
        }

        ValidationResult result = ruleRegistry
                .forBusinessType(businessTypeOf(close))
                .validate(contextFor(close));
        if (!result.isValid()) {
            throw CashCloseExceptions.validationFailed(String.join("; ", result.errors()));
        }

        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.SUBMITTED);
        close.setSubmittedBy(TenantContext.current().userId());
        close.setSubmittedAt(Instant.now());
        close.setNote(note);

        // The DB trigger snapshots the thresholds and computes is_late here, so
        // those two values do have to be read back.
        entityManager.flush();
        entityManager.refresh(close);

        recordDecision(close, ApprovalAction.SUBMIT, previous, CloseStatus.SUBMITTED, note);
        return mapper.toResponse(view(close));
    }

    @Override
    @Transactional
    public CashCloseResponse approve(UUID cashCloseId, String reviewNote) {
        CashClose close = requireClose(cashCloseId);
        requireReviewer(close.getBranchId());
        requireTransition(close, CloseStatus.APPROVED);

        if (movementRepository.existsByCashCloseIdAndApprovalStatus(cashCloseId, MovementStatus.PENDING)) {
            throw CashCloseExceptions.movementsPending();
        }

        // The reviewer's comment goes into the decision ledger, not onto the close:
        // a close rejected then resubmitted has several comments, and a single
        // column would keep only the last one.
        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.APPROVED);

        recordDecision(close, ApprovalAction.APPROVE, previous, CloseStatus.APPROVED, reviewNote);
        return mapper.toResponse(view(close));
    }

    @Override
    @Transactional
    public CashCloseResponse reject(UUID cashCloseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Rejecting a close requires a reason");
        }
        CashClose close = requireClose(cashCloseId);
        requireReviewer(close.getBranchId());
        requireTransition(close, CloseStatus.REJECTED);

        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.REJECTED);

        recordDecision(close, ApprovalAction.REJECT, previous, CloseStatus.REJECTED, reason);
        return mapper.toResponse(view(close));
    }

    @Override
    @Transactional
    public CashCloseResponse reopen(UUID cashCloseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Reopening a close requires a reason");
        }
        CashClose close = requireClose(cashCloseId);
        requireReviewer(close.getBranchId());
        requireTransition(close, CloseStatus.DRAFT);

        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.DRAFT);

        recordDecision(close, ApprovalAction.REQUEST_CHANGES, previous, CloseStatus.DRAFT, reason);
        return mapper.toResponse(view(close));
    }

    // ------------------------------------------------------------------------
    // Cash ledger
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashMovementResponse> listMovements(CashMovementListFilter filter, Pageable pageable) {
        ValidationUtils.requireValidDateRange(filter.getFromDate(), filter.getToDate());
        MovementStatus approvalStatus = parseEnum(
                filter.getApprovalStatus(), MovementStatus.class, "movement approval status");
        EffectType effectType = parseEnum(filter.getEffectType(), EffectType.class, "effect type");

        return PaginationUtils.toPagedResponse(
                movementRepository.findAll(movementFilter(filter, approvalStatus, effectType), pageable),
                this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovementResponse> getMovements(UUID cashCloseId) {
        requireClose(cashCloseId);
        return movementRepository.findByCashCloseId(cashCloseId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public CashMovementResponse addMovement(UUID cashCloseId, AddMovementRequest request) {
        CashClose close = requireClose(cashCloseId);
        if (!close.isEditable()) {
            throw CashCloseExceptions.closeFrozen(
                    "Close %s is %s - its lines are frozen"
                            .formatted(close.getCashCloseCode(), close.getStatus()));
        }

        MovementKind kind = resolveKind(close, request.getKindCode());

        CashMovement movement = new CashMovement();
        movement.setMovementId(UUID.randomUUID());
        movement.setCashCloseId(cashCloseId);
        movement.setBusinessId(close.getBusinessId());
        movement.setKindSk(kind.getKindSk());
        movement.setEffectType(kind.getEffectType());
        movement.setSignedAmount(signFor(kind, request.getAmount()));
        movement.setStaffUserId(request.getStaffUserId());
        movement.setDescription(request.getDescription());
        movement.setReceiptAttachmentId(request.getReceiptAttachmentId());
        movement.setCreatedBy(TenantContext.current().userId());

        return mapper.toResponse(movementRepository.save(movement), kind);
    }

    @Override
    @Transactional
    public CashMovementResponse updateMovement(UUID movementId, UpdateMovementRequest request) {
        CashMovement movement = requireMovement(movementId);
        if (!movement.isEditable()) {
            throw CashCloseExceptions.movementDecided(
                    "Line is %s - reopen it before changing the amount or kind"
                            .formatted(movement.getApprovalStatus()));
        }
        CashClose close = requireClose(movement.getCashCloseId());

        MovementKind kind = request.getKindCode() == null
                ? entityManager.find(MovementKind.class, movement.getKindSk())
                : resolveKind(close, request.getKindCode());

        if (request.getAmount() != null || request.getKindCode() != null) {
            BigDecimal amount = request.getAmount() != null
                    ? request.getAmount()
                    : movement.getSignedAmount().abs();
            movement.setKindSk(kind.getKindSk());
            movement.setEffectType(kind.getEffectType());
            movement.setSignedAmount(signFor(kind, amount));
        }
        if (request.getStaffUserId() != null)        movement.setStaffUserId(request.getStaffUserId());
        if (request.getDescription() != null)        movement.setDescription(request.getDescription());
        if (request.getReceiptAttachmentId() != null) movement.setReceiptAttachmentId(request.getReceiptAttachmentId());

        return mapper.toResponse(movement, kind);
    }

    @Override
    @Transactional
    public CashMovementResponse approveMovement(UUID movementId, String note) {
        CashMovement movement = requireMovement(movementId);
        requireReviewer(requireClose(movement.getCashCloseId()).getBranchId());
        requireMovementTransition(movement, MovementStatus.APPROVED);
        movement.approve(TenantContext.current().userId(), Instant.now(), note);
        return toDto(movement);
    }

    @Override
    @Transactional
    public CashMovementResponse rejectMovement(UUID movementId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Rejecting a line requires a reason");
        }
        CashMovement movement = requireMovement(movementId);
        requireReviewer(requireClose(movement.getCashCloseId()).getBranchId());
        requireMovementTransition(movement, MovementStatus.REJECTED);
        movement.reject(TenantContext.current().userId(), Instant.now(), reason);
        return toDto(movement);
    }

    @Override
    @Transactional
    public CashMovementResponse reopenMovement(UUID movementId, String reason) {
        CashMovement movement = requireMovement(movementId);
        requireReviewer(requireClose(movement.getCashCloseId()).getBranchId());
        requireMovementTransition(movement, MovementStatus.PENDING);
        movement.reopen(reason);
        return toDto(movement);
    }

    // ------------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CloseDecisionResponse> getHistory(UUID cashCloseId) {
        requireClose(cashCloseId);
        return mapper.toCloseDecisionList(
                closeDecisionRepository.findByCashCloseIdOrderByActedAtAsc(cashCloseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementDecisionResponse> getMovementHistory(UUID cashCloseId) {
        requireClose(cashCloseId);
        return mapper.toMovementDecisionList(
                movementDecisionRepository.findByCashCloseId(cashCloseId));
    }

    // ------------------------------------------------------------------------

    /**
     * Combines a close with its calculated figures and its approver.
     *
     * <p>{@code CashCloseCalc} declares {@code @Synchronize} on the three source
     * tables, so Hibernate flushes pending changes before reading the view.
     */
    private CashCloseView view(CashClose close) {
        var approval = closeDecisionRepository.findFirstByCashCloseIdAndNewStatusOrderByActedAtDesc(
                close.getCashCloseId(), CloseStatus.APPROVED);
        return CashCloseView.of(
                close,
                calcRepository.findById(close.getCashCloseId()).orElse(null),
                approval.map(CashCloseDecision::getActedBy).orElse(null),
                approval.map(CashCloseDecision::getActedAt).orElse(null));
    }

    private CashMovementResponse toDto(CashMovement movement) {
        return mapper.toResponse(movement, entityManager.find(MovementKind.class, movement.getKindSk()));
    }

    /**
     * Resolves the catalogue version in force on the close's BUSINESS DATE, not
     * {@code now()}: a 15 Jun close submitted late on 17 Jun still follows the
     * 15 Jun rules, and the line points at that version forever.
     */
    private MovementKind resolveKind(CashClose close, String kindCode) {
        return movementKindRepository
                .resolveAt(kindCode, close.getBusinessId(), close.getBusinessDate())
                .orElseThrow(() -> CashCloseExceptions.unknownMovementKind(
                        "No movement kind \"%s\" is in force on %s"
                                .formatted(kindCode, close.getBusinessDate())));
    }

    /**
     * The sign is decided by the KIND, never by the person entering it.
     *
     * <p>{@code NO_CASH_FLOW} is the one ambiguous case - no cash moved, yet the
     * drawer is off. The convention is "treat as short" (a customer who did not
     * pay). The over case, a POS double-count, is rarer and will need its own call
     * with an explicit sign.
     */
    private static BigDecimal signFor(MovementKind kind, BigDecimal positiveAmount) {
        return switch (kind.getEffectType()) {
            case CASH_IN -> positiveAmount;
            case CASH_OUT, NO_CASH_FLOW -> positiveAmount.negate();
        };
    }

    private CashClose requireClose(UUID id) {
        CashClose close = closeRepository.findById(id).orElseThrow(CashCloseExceptions::cashCloseNotFound);
        if (!AccessPrincipal.current().branches().contains(close.getBranchId())) throw CashCloseExceptions.cashCloseNotFound();
        return close;
    }

    private CashMovement requireMovement(UUID id) {
        CashMovement movement = movementRepository.findById(id).orElseThrow(CashCloseExceptions::movementNotFound);
        requireClose(movement.getCashCloseId());
        return movement;
    }

    private static void requireReviewer(UUID branchId) {
        AccessPrincipal.current().requireBranch(branchId, UserRole.ADMIN, UserRole.MANAGER, UserRole.ACCOUNTANT);
    }

    private void requireTransition(CashClose close, CloseStatus target) {
        if (!close.getStatus().canTransitionTo(target)) {
            throw CashCloseExceptions.illegalTransition(
                    "Cannot move %s -> %s".formatted(close.getStatus(), target));
        }
    }

    private void requireMovementTransition(CashMovement movement, MovementStatus target) {
        if (!movement.getApprovalStatus().canTransitionTo(target)) {
            throw CashCloseExceptions.illegalMovementTransition(
                    "Cannot move line %s -> %s".formatted(movement.getApprovalStatus(), target));
        }
    }

    private CashCloseContext contextFor(CashClose close) {
        return new CashCloseContext(
                close,
                calcRepository.findById(close.getCashCloseId()).orElse(null),
                businessTypeOf(close),
                nz(close.getAppliedDiffAllowedAbs()),
                nz(close.getAppliedDiffAlertAbs()),
                BigDecimal.ZERO);
    }

    /** Cross-schema read into {@code identity} - shared database, no network call. */
    private BusinessType businessTypeOf(CashClose close) {
        Business business = entityManager.find(Business.class, close.getBusinessId());
        return business == null ? BusinessType.CAFE : business.getBusinessType();
    }

    private void recordDecision(CashClose close, ApprovalAction action,
                                CloseStatus from, CloseStatus to, String note) {
        CashCloseDecision decision = new CashCloseDecision();
        decision.setDecisionId(UUID.randomUUID());
        decision.setCashCloseId(close.getCashCloseId());
        decision.setBusinessId(close.getBusinessId());
        decision.setAction(action);
        decision.setActedBy(TenantContext.current().userId());
        decision.setActedRole(AccessPrincipal.current().branchRoles().get(close.getBranchId()).name());
        decision.setOldStatus(from);
        decision.setNewStatus(to);
        decision.setNote(note);
        closeDecisionRepository.save(decision);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static Specification<CashClose> cashCloseFilter(
            CashCloseListFilter filter,
            CloseStatus status,
            ExpectedCashSource expectedCashSource) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("branchId").in(AccessPrincipal.current().branches()));
            if (filter.getBranchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.getBranchId()));
            }
            if (filter.getShiftTypeId() != null) {
                predicates.add(cb.equal(root.get("shiftTypeId"), filter.getShiftTypeId()));
            }
            if (filter.getCreatedBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), filter.getCreatedBy()));
            }
            if (filter.getSubmittedBy() != null) {
                predicates.add(cb.equal(root.get("submittedBy"), filter.getSubmittedBy()));
            }
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("businessDate"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("businessDate"), filter.getToDate()));
            }
            if (hasText(filter.getCashCloseCode())) {
                predicates.add(cb.equal(root.get("cashCloseCode"), filter.getCashCloseCode().trim()));
            }
            if (expectedCashSource != null) {
                predicates.add(cb.equal(root.get("expectedCashSource"), expectedCashSource));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), CloseStatus.VOIDED));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<CashMovement> movementFilter(
            CashMovementListFilter filter,
            MovementStatus approvalStatus,
            EffectType effectType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            assert query != null;
            var closeRoot = query.from(CashClose.class);
            predicates.add(closeRoot.get("branchId").in(AccessPrincipal.current().branches()));
            predicates.add(cb.equal(root.get("cashCloseId"), closeRoot.get("cashCloseId")));

            if (filter.getCashCloseId() != null) {
                predicates.add(cb.equal(root.get("cashCloseId"), filter.getCashCloseId()));
            }
            if (filter.getBranchId() != null) {
                predicates.add(cb.equal(closeRoot.get("branchId"), filter.getBranchId()));
            }
            if (filter.getShiftTypeId() != null) {
                predicates.add(cb.equal(closeRoot.get("shiftTypeId"), filter.getShiftTypeId()));
            }
            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(closeRoot.get("businessDate"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(closeRoot.get("businessDate"), filter.getToDate()));
            }
            if (filter.getStaffUserId() != null) {
                predicates.add(cb.equal(root.get("staffUserId"), filter.getStaffUserId()));
            }
            if (filter.getCreatedBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), filter.getCreatedBy()));
            }
            if (approvalStatus != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), approvalStatus));
            }
            if (effectType != null) {
                predicates.add(cb.equal(root.get("effectType"), effectType));
            }
            if (hasText(filter.getKindCode())) {
                var kindRoot = query.from(MovementKind.class);
                predicates.add(cb.equal(root.get("kindSk"), kindRoot.get("kindSk")));
                predicates.add(cb.equal(cb.upper(kindRoot.get("kindCode")), normalizeCode(filter.getKindCode())));
            }
            if (isContentQuery(query)) {
                query.orderBy(cb.desc(closeRoot.get("businessDate")), cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType, String label) {
        if (!hasText(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw CashCloseExceptions.invalidFilter("Unknown %s: %s".formatted(label, raw));
        }
    }

    private static String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isContentQuery(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return resultType != Long.class && resultType != long.class;
    }

    private static String buildCode(OpenDraftRequest r) {
        return "CC-%s-%s-%s".formatted(
                r.getBusinessDate(), shortId(r.getBranchId()), shortId(r.getShiftTypeId()));
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }
}

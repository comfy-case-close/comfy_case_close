package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.request.AddMovementRequest;
import com.fnbx.cashclose.dto.request.AttachFileRequest;
import com.fnbx.cashclose.dto.response.CloseAttachmentResponse;
import com.fnbx.cashclose.entity.CloseAttachment;
import com.fnbx.cashclose.service.CashCloseNotifications;
import com.fnbx.cashclose.service.EffectiveConfig;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.cashclose.dto.request.DifferenceDirection;
import com.fnbx.cashclose.dto.response.DaySummaryResponse;
import com.fnbx.cashclose.dto.response.DenominationResponse;
import com.fnbx.cashclose.dto.response.ShiftTypeResponse;
import com.fnbx.identity.entity.ShiftType;
import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.request.ReplaceDenominationsRequest;
import com.fnbx.cashclose.dto.request.UpdateMovementRequest;
import com.fnbx.cashclose.dto.request.SubmitCashCloseRequest;
import com.fnbx.cashclose.dto.request.SubmissionAttachmentRequest;
import com.fnbx.cashclose.dto.request.CorrectCashCloseRequest;
import com.fnbx.cashclose.dto.response.CashCloseResponse;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.CloseDecisionResponse;
import com.fnbx.cashclose.dto.response.DenominationLineResponse;
import com.fnbx.cashclose.dto.response.DenominationSetResponse;
import com.fnbx.cashclose.dto.response.MovementKindResponse;
import com.fnbx.cashclose.dto.response.MovementDecisionResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.CashCloseDecision;
import com.fnbx.cashclose.entity.CashDenominationLine;
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
import com.fnbx.cashclose.repository.DenominationRepository;
import com.fnbx.cashclose.repository.MovementKindRepository;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.cashclose.service.CashCloseView;
import com.fnbx.cashclose.service.ShiftSalesLookup;
import com.fnbx.cashclose.service.rule.CashCloseContext;
import com.fnbx.cashclose.service.rule.RuleRegistry;
import com.fnbx.cashclose.service.rule.ValidationResult;
import com.fnbx.identity.entity.Business;
import com.fnbx.platform.entity.Denomination;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.enums.EffectType;
import com.fnbx.shared.enums.FileKind;
import com.fnbx.shared.enums.BusinessType;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.security.Permission;
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
import com.fnbx.cashclose.service.FundWithdrawalService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Implementation of {@link CashCloseService}.
 *
 * <h2>What is deliberately absent</h2>
 * <ul>
 *   <li><b>No stored total writes.</b> Calculations come from a view. Writes are
 *       flushed and managed view rows refreshed before returning totals.</li>
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
    private final DenominationRepository denominationRepository;
    private final BranchAccessGuard branchAccess;
    private final RuleRegistry ruleRegistry;
    private final ShiftSalesLookup shiftSalesLookup;
    private final CashCloseMapper mapper;
    private final EntityManager entityManager;
    private final CashCloseNotifications notifications;
    private final EffectiveConfig config;
    private final FundWithdrawalService fundWithdrawals;

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    @Override
    @Transactional
    public CashCloseResponse submit(UUID branchId, SubmitCashCloseRequest request) {
        TenantContext tenant = TenantContext.current();
        UUID businessId = tenant.businessId();
        branchAccess.require(branchId, Permission.CLOSE_SUBMIT);
        if (request.getBusinessDate().isAfter(LocalDate.now(ZoneId.of(business().getTimezone()))))
            throw CashCloseExceptions.validationFailed("businessDate cannot be in the future");

        ShiftType shift = entityManager.find(ShiftType.class, request.getShiftTypeId());
        if (shift == null || !shift.isActive() || !businessId.equals(shift.getBusinessId()))
            throw CashCloseExceptions.validationFailed("Select an active shift type belonging to this business");
        if (closeRepository.existsByBranchIdAndShiftTypeIdAndBusinessDateAndStatusNot(
                branchId, request.getShiftTypeId(), request.getBusinessDate(), CloseStatus.VOIDED))
            throw CashCloseExceptions.closeAlreadyExists();
        if (request.getDenominations() == null || request.getDenominations().getCounts() == null
                || request.getDenominations().getCounts().isEmpty())
            throw CashCloseExceptions.noDenominationCount();

        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBusinessId(businessId);
        close.setCreatedBy(tenant.userId());
        close.setSubmittedBy(tenant.userId());
        close.setSubmittedAt(Instant.now());
        close.setBranchId(branchId);
        close.setShiftTypeId(request.getShiftTypeId());
        close.setBusinessDate(request.getBusinessDate());
        close.setCashCloseCode(buildCode(branchId, request));
        close.setStatus(CloseStatus.SUBMITTED);
        close.setNote(request.getNote());
        shiftSalesLookup.findLatest(branchId, request.getShiftTypeId(), request.getBusinessDate())
                .ifPresentOrElse(sales -> {
                    close.setPosExpectedCash(sales.getCashSales());
                    close.setExpectedCashSource(ExpectedCashSource.POS_SYNC);
                    close.setPosShiftSalesId(sales.getShiftSalesId());
                }, () -> {
                    close.setPosExpectedCash(BigDecimal.ZERO);
                    close.setExpectedCashSource(ExpectedCashSource.MANUAL);
                });
        if (request.getFigures() != null) {
            if (request.getFigures().getPosExpectedCash() != null) {
                if (close.isExpectedCashLocked()) throw CashCloseExceptions.expectedCashLocked(
                        "POS-synced expected cash cannot be overridden");
                close.setPosExpectedCash(request.getFigures().getPosExpectedCash());
            }
            if (request.getFigures().getWithdrawalAmount() != null) {
                branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
                close.setWithdrawalAmount(request.getFigures().getWithdrawalAmount());
            }
        }
        CashClose submittedClose = closeRepository.save(close);
        entityManager.flush();
        entityManager.refresh(submittedClose);
        fundWithdrawals.syncCloseWithdrawal(submittedClose, request.getFigures(), null);
        if (request.getAttachments() != null)
            for (SubmissionAttachmentRequest attachment : request.getAttachments())
                persistSubmissionAttachment(submittedClose, attachment);
        requireSubmissionImages(submittedClose, request.getAttachments(), request.getMovements());
        replaceDenominationsForClose(submittedClose, request.getDenominations());
        if (denominationLineRepository.countByCashCloseId(submittedClose.getCashCloseId()) == 0)
            throw CashCloseExceptions.noDenominationCount();
        if (request.getMovements() != null)
            for (AddMovementRequest movement : request.getMovements())
                addMovement(branchId, submittedClose.getCashCloseId(), movement);

        CashCloseCalc calculated = view(submittedClose).calc();
        if (calculated != null && calculated.getCashRemaining().signum() < 0)
            throw CashCloseExceptions.validationFailed("Cash remaining cannot be negative");
        if (calculated != null && calculated.absUnexplained().compareTo(
                config.number(branchId, "DIFF_NOTE_REQUIRED_ABS", new BigDecimal("50000"))) >= 0
                && !hasText(request.getNote()))
            throw CashCloseExceptions.validationFailed("A note is required for this cash difference");
        ValidationResult result = ruleRegistry.forBusinessType(businessTypeOf(submittedClose)).validate(contextFor(submittedClose));
        if (!result.isValid())
            throw CashCloseExceptions.validationFailed(String.join("; ", result.errors()));
        recordDecision(submittedClose, ApprovalAction.SUBMIT, null, CloseStatus.SUBMITTED,
                request.getNote(), Permission.CLOSE_SUBMIT);
        notifications.submitted(submittedClose);
        return mapper.toResponse(view(submittedClose));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashCloseResponse> listCashCloses(UUID branchId, CashCloseListFilter filter, Pageable pageable) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        if (filter.getBranchId() != null && !branchId.equals(filter.getBranchId())) throw CashCloseExceptions.branchHeaderMismatch();
        filter.setBranchId(branchId);
        ValidationUtils.requireValidDateRange(filter.getFromDate(), filter.getToDate());
        LocalDate today = LocalDate.now(ZoneId.of(business().getTimezone()));
        if ((filter.getFromDate() != null && filter.getFromDate().isAfter(today))
                || (filter.getToDate() != null && filter.getToDate().isAfter(today))) {
            throw CashCloseExceptions.invalidFilter("Cash close date filters cannot be in the future");
        }
        CloseStatus status = parseEnum(filter.getStatus(), CloseStatus.class, "cash close status");
        ExpectedCashSource expectedCashSource = parseEnum(
                filter.getExpectedCashSource(), ExpectedCashSource.class, "expected cash source");

        return PaginationUtils.toPagedResponse(
                closeRepository.findAll(cashCloseFilter(filter, status, expectedCashSource), pageable),
                close -> mapper.toResponse(view(close)));
    }

    @Override
    @Transactional(readOnly = true)
    public CashCloseResponse getById(UUID branchId, UUID cashCloseId) {
        return mapper.toResponse(view(requireClose(branchId, cashCloseId)));
    }

    @Override
    @Transactional
    public CashCloseResponse approve(UUID branchId, UUID cashCloseId, String reviewNote) {
        Scoped scoped = requireCloseAtBranch(branchId, cashCloseId, Permission.CLOSE_REVIEW);
        CashClose close = scoped.close();
        requireTransition(close, CloseStatus.APPROVED);
        fundWithdrawals.requireConfirmed(close);

        // One close-level approval also decides optional expense categories. A
        // configured required category must first receive its own line decision.
        List<CashMovement> optional = new ArrayList<>();
        for (CashMovement movement : movementRepository.findByCashCloseIdAndApprovalStatus(cashCloseId, MovementStatus.PENDING)) {
            MovementKind kind = entityManager.find(MovementKind.class, movement.getKindSk());
            String key = separateReviewKey(kind == null ? null : kind.getExpenseCategory());
            if (key == null || config.bool(branchId, key, false)) throw CashCloseExceptions.movementsPending();
            optional.add(movement);
        }
        for (CashMovement movement : optional) {
            movement.approve(TenantContext.current().userId(), Instant.now(), "Approved with cash close");
        }
        entityManager.flush();

        // The reviewer's comment goes into the decision ledger, not onto the close:
        // a close rejected then resubmitted has several comments, and a single
        // column would keep only the last one.
        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.APPROVED);

        recordDecision(close, ApprovalAction.APPROVE, previous, CloseStatus.APPROVED, reviewNote, scoped.permission());
        return mapper.toResponse(view(close));
    }

    private static String separateReviewKey(String category) {
        if (category == null) return null;
        return switch (category) {
            case "SUPPLY" -> "REQUIRE_APPROVAL_SUPPLY";
            case "GOODS_SHIPPING" -> "REQUIRE_APPROVAL_GOODS_OR_SHIPPING";
            case "REFUND" -> "REQUIRE_APPROVAL_REFUND";
            case "STAFF_PARKING" -> "REQUIRE_APPROVAL_STAFF_PARKING";
            case "OTHER" -> "REQUIRE_APPROVAL_OTHER";
            default -> null;
        };
    }

    @Override
    @Transactional
    public CashCloseResponse reject(UUID branchId, UUID cashCloseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Rejecting a close requires a reason");
        }
        Scoped scoped = requireCloseAtBranch(branchId, cashCloseId, Permission.CLOSE_REVIEW);
        CashClose close = scoped.close();
        requireTransition(close, CloseStatus.REJECTED);

        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.REJECTED);

        recordDecision(close, ApprovalAction.REJECT, previous, CloseStatus.REJECTED, reason, scoped.permission());
        return mapper.toResponse(view(close));
    }

    @Override
    @Transactional
    public CashCloseResponse correctCashClose(UUID branchId, UUID cashCloseId,
                                              CorrectCashCloseRequest request) {
        Scoped scoped = requireCloseAtBranch(branchId, cashCloseId, Permission.CLOSE_CORRECT);
        CashClose close = scoped.close();
        if (request.getDenominations() == null && request.getFigures() == null && request.getNote() == null) {
            throw CashCloseExceptions.validationFailed("A correction must change figures or denominations");
        }
        if (close.getStatus() == CloseStatus.VOIDED) {
            throw CashCloseExceptions.illegalTransition("Only a submitted close can be corrected");
        }

        CloseStatus previous = close.getStatus();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.getDenominations() != null) {
            Map<String, Integer> before = countSnapshot(close);
            Map<String, Integer> after = countSnapshot(request.getDenominations());
            if (!before.equals(after))
                changes.put("denominations", Map.of("before", before, "after", after));
        }
        if (request.getFigures() != null) {
            Map<String, Object> figures = new LinkedHashMap<>();
            if (request.getFigures().getPosExpectedCash() != null) {
                if (close.isExpectedCashLocked()) throw CashCloseExceptions.expectedCashLocked(
                        "POS-synced expected cash cannot be corrected by hand");
                if (close.getPosExpectedCash().compareTo(request.getFigures().getPosExpectedCash()) != 0)
                    figures.put("posExpectedCash", Map.of("before", close.getPosExpectedCash(),
                            "after", request.getFigures().getPosExpectedCash()));
            }
            if (request.getFigures().getWithdrawalAmount() != null) {
                if (close.getWithdrawalAmount().compareTo(request.getFigures().getWithdrawalAmount()) != 0)
                    figures.put("withdrawalAmount", Map.of("before", close.getWithdrawalAmount(),
                            "after", request.getFigures().getWithdrawalAmount()));
            }
            if (!figures.isEmpty()) changes.put("figures", figures);
        }
        if (request.getNote() != null && !request.getNote().equals(close.getNote())) {
            Map<String, Object> noteChange = new LinkedHashMap<>();
            noteChange.put("before", close.getNote());
            noteChange.put("after", request.getNote());
            changes.put("note", noteChange);
        }
        Map<String, Object> withdrawalChange = fundWithdrawals.describeCloseCorrection(close, request.getFigures());
        if (!withdrawalChange.isEmpty()) changes.put("withdrawal", withdrawalChange);
        if (changes.isEmpty()) throw CashCloseExceptions.validationFailed("A correction must contain a changed value");

        // Move out of APPROVED before touching frozen child rows. Both operations and
        // the decision insert share this transaction, so a failed edit rolls back all three.
        close.setStatus(CloseStatus.PENDING_REVIEW);
        entityManager.flush();
        if (request.getFigures() != null) {
            if (request.getFigures().getPosExpectedCash() != null)
                close.setPosExpectedCash(request.getFigures().getPosExpectedCash());
            if (request.getFigures().getWithdrawalAmount() != null)
                close.setWithdrawalAmount(request.getFigures().getWithdrawalAmount());
        }
        fundWithdrawals.syncCloseWithdrawal(close, request.getFigures(), request.getEditReason());
        if (request.getDenominations() != null)
            replaceDenominationsForClose(close, request.getDenominations());
        if (denominationLineRepository.countByCashCloseId(cashCloseId) == 0)
            throw CashCloseExceptions.noDenominationCount();
        if (request.getNote() != null) close.setNote(request.getNote());
        entityManager.flush();
        CashCloseCalc calculated = view(close).calc();
        if (calculated != null && calculated.getCashRemaining().signum() < 0)
            throw CashCloseExceptions.validationFailed("Cash remaining cannot be negative");
        ValidationResult result = ruleRegistry.forBusinessType(businessTypeOf(close)).validate(contextFor(close));
        if (!result.isValid())
            throw CashCloseExceptions.validationFailed(String.join("; ", result.errors()));
        recordDecision(close, ApprovalAction.EDIT, previous, CloseStatus.PENDING_REVIEW,
                request.getEditReason(), scoped.permission(), changes);
        return mapper.toResponse(view(close));
    }

    private Map<String, Integer> countSnapshot(CashClose close) {
        Map<Short, BigDecimal> values = new HashMap<>();
        denominationRepository.findByCurrencyCode(business().getCurrencyCode())
                .forEach(d -> values.put(d.getDenominationId(), d.getFaceValue()));
        Map<String, Integer> result = new TreeMap<>();
        denominationLineRepository.findByCashCloseId(close.getCashCloseId())
                .forEach(line -> result.put(values.get(line.getDenominationId())
                        .stripTrailingZeros().toPlainString(), line.getQuantity()));
        return result;
    }

    private Map<String, Integer> countSnapshot(ReplaceDenominationsRequest request) {
        Map<String, Integer> result = new TreeMap<>();
        request.getCounts().forEach(line -> {
            if (line.getQuantity() > 0)
                result.put(line.getFaceValue().stripTrailingZeros().toPlainString(), line.getQuantity());
        });
        return result;
    }

    @Override
    @Transactional
    public CashCloseResponse voidClose(UUID branchId, UUID cashCloseId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Voiding a close requires a reason");
        }
        Scoped scoped = requireCloseAtBranch(branchId, cashCloseId, Permission.CLOSE_VOID);
        CashClose close = scoped.close();
        requireTransition(close, CloseStatus.VOIDED);

        CloseStatus previous = close.getStatus();
        close.setStatus(CloseStatus.VOIDED);
        // Not written by a trigger, and ck_close_voided_fields requires the two to
        // agree: a VOIDED row without a voided_at is refused by the database.
        close.setVoidedAt(Instant.now());

        recordDecision(close, ApprovalAction.VOID, previous, CloseStatus.VOIDED, reason, scoped.permission());
        return mapper.toResponse(view(close));
    }

    // ------------------------------------------------------------------------
    // Denomination count
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public DenominationSetResponse getDenominations(UUID branchId, UUID cashCloseId) {
        return denominationSet(requireClose(branchId, cashCloseId));
    }

    private void replaceDenominationsForClose(CashClose close, ReplaceDenominationsRequest request) {
        UUID cashCloseId = close.getCashCloseId();

        NavigableMap<BigDecimal, Denomination> catalogue = countableDenominations();
        List<CashDenominationLine> lines = new ArrayList<>();
        // TreeMap, so 500000 and 500000.00 are the same denomination here exactly as
        // they are to the UNIQUE constraint on (cash_close_id, denomination_id).
        NavigableMap<BigDecimal, Boolean> seen = new TreeMap<>();

        for (var count : request.getCounts()) {
            BigDecimal faceValue = count.getFaceValue();
            if (seen.put(faceValue, Boolean.TRUE) != null) {
                throw CashCloseExceptions.duplicateDenomination(
                        "Denomination %s appears more than once in the count".formatted(faceValue.toPlainString()));
            }
            Denomination denomination = catalogue.get(faceValue);
            if (denomination == null) {
                throw CashCloseExceptions.unknownDenomination(
                        "No active denomination with face value %s".formatted(faceValue.toPlainString()));
            }
            // quantity 0 means "none left of this note". It is a legitimate thing for a
            // client to send from a grid that always posts every row, and it is not a
            // row: cash_denomination_line CHECKs quantity > 0.
            if (count.getQuantity() == 0) {
                continue;
            }
            CashDenominationLine line = new CashDenominationLine();
            line.setLineId(UUID.randomUUID());
            line.setCashCloseId(cashCloseId);
            line.setBusinessId(close.getBusinessId());
            line.setDenominationId(denomination.getDenominationId());
            line.setQuantity(count.getQuantity());
            lines.add(line);
        }

        // Delete and insert must not be reordered into each other: the unique index on
        // (cash_close_id, denomination_id) would reject the new row before the old one
        // is gone. Hibernate orders inserts before deletes within a flush, so the
        // delete is flushed on its own first.
        denominationLineRepository.deleteByCashCloseId(cashCloseId);
        entityManager.flush();
        denominationLineRepository.saveAll(lines);
        entityManager.flush();

    }

    // ------------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<MovementKindResponse> listMovementKinds(UUID branchId, LocalDate businessDate) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        LocalDate on = businessDate == null ? LocalDate.now(java.time.ZoneId.of(business().getTimezone())) : businessDate;
        return mapper.toMovementKindList(
                movementKindRepository.findAllEffective(TenantContext.current().businessId(), on));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftTypeResponse> listShiftTypes(UUID branchId) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        return entityManager.createQuery("SELECT s FROM ShiftType s WHERE s.businessId = :business AND s.active = true ORDER BY s.sortOrder, s.shiftCode", ShiftType.class)
                .setParameter("business", TenantContext.current().businessId()).getResultList().stream()
                .map(s -> new ShiftTypeResponse(s.getShiftTypeId(), s.getShiftCode(), s.getShiftName(),
                        s.getSortOrder(), s.getSuggestedStartTime(), s.getSuggestedEndTime(), s.getSubmitDeadline()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DenominationResponse> listDenominations(UUID branchId) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        return countableDenominations().descendingMap().values().stream()
                .map(d -> new DenominationResponse(d.getDenominationId(), d.getCurrencyCode(), d.getFaceValue())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DaySummaryResponse getDaySummary(UUID branchId, UUID cashCloseId) {
        CashClose anchor = requireClose(branchId, cashCloseId);
        var closes = entityManager.createQuery("""
                SELECT c FROM CashClose c WHERE c.branchId = :branch AND c.businessDate = :date
                AND c.status <> :voided ORDER BY c.createdAt, c.cashCloseId
                """, CashClose.class)
                .setParameter("branch", anchor.getBranchId()).setParameter("date", anchor.getBusinessDate())
                .setParameter("voided", CloseStatus.VOIDED).getResultList().stream()
                .map(c -> mapper.toResponse(view(c))).toList();
        return new DaySummaryResponse(anchor.getBranchId(), anchor.getBusinessDate(), closes);
    }

    // ------------------------------------------------------------------------
    // Cash ledger
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CloseAttachmentResponse> getAttachments(UUID branchId, UUID cashCloseId) {
        requireClose(branchId, cashCloseId);
        return entityManager.createQuery("SELECT a FROM CloseAttachment a WHERE a.cashCloseId = :id ORDER BY a.attachmentId", CloseAttachment.class)
                .setParameter("id", cashCloseId).getResultList().stream().map(this::attachmentResponse).toList();
    }

    @Override
    @Transactional
    public CloseAttachmentResponse attachFile(UUID branchId, UUID cashCloseId, AttachFileRequest request) {
        CashClose close = requireCloseAtBranch(branchId, cashCloseId, Permission.CLOSE_EDIT).close();
        if (!close.isEditable()) throw CashCloseExceptions.closeFrozen("Close is frozen");
        StoredFile file = entityManager.find(StoredFile.class, request.fileId());
        if (file == null || !close.getBusinessId().equals(file.getBusinessId())
                || (file.getBranchId() != null && !branchId.equals(file.getBranchId()))) {
            throw CashCloseExceptions.validationFailed("File is unavailable at this branch");
        }
        CloseAttachment attachment = new CloseAttachment();
        attachment.setAttachmentId(UUID.randomUUID());
        attachment.setCashCloseId(cashCloseId);
        attachment.setBusinessId(close.getBusinessId());
        attachment.setFileId(file.getFileId());
        attachment.setFileKind(request.fileKind());
        attachment.setAttachedBy(TenantContext.current().userId());
        entityManager.persist(attachment);
        return attachmentResponse(attachment);
    }

    private CloseAttachmentResponse attachmentResponse(CloseAttachment attachment) {
        return new CloseAttachmentResponse(attachment.getAttachmentId(), attachment.getCashCloseId(),
                attachment.getFileId(), attachment.getFileKind().name());
    }

    private void persistSubmissionAttachment(CashClose close, SubmissionAttachmentRequest request) {
        StoredFile file = entityManager.find(StoredFile.class, request.fileId());
        if (file == null || !close.getBusinessId().equals(file.getBusinessId())
                || (file.getBranchId() != null && !close.getBranchId().equals(file.getBranchId())))
            throw CashCloseExceptions.validationFailed("File is unavailable at this branch");
        CloseAttachment attachment = new CloseAttachment();
        attachment.setAttachmentId(request.attachmentId() == null ? UUID.randomUUID() : request.attachmentId());
        attachment.setCashCloseId(close.getCashCloseId());
        attachment.setBusinessId(close.getBusinessId());
        attachment.setFileId(file.getFileId());
        attachment.setFileKind(request.fileKind());
        attachment.setAttachedBy(TenantContext.current().userId());
        entityManager.persist(attachment);
    }

    private void requireSubmissionImages(CashClose close, List<SubmissionAttachmentRequest> attachments,
                                         List<AddMovementRequest> movements) {
        List<SubmissionAttachmentRequest> files = attachments == null ? List.of() : attachments;
        if (config.bool(close.getBranchId(), "REQUIRE_POS_IMAGE", false)
                && files.stream().noneMatch(a -> a.fileKind() == FileKind.POS_REPORT))
            throw CashCloseExceptions.validationFailed("A POS report image is required");
        if (config.bool(close.getBranchId(), "REQUIRE_CASH_IMAGE", false)
                && files.stream().noneMatch(a -> a.fileKind() == FileKind.CASH_DRAWER_PHOTO))
            throw CashCloseExceptions.validationFailed("A cash drawer image is required");
        boolean unpaidBill = movements != null && movements.stream()
                .anyMatch(m -> "UNPAID_BILL".equals(m.getKindCode()));
        if (unpaidBill && config.bool(close.getBranchId(), "REQUIRE_UNPAID_BILL_REPAYMENT", true)
                && files.stream().noneMatch(a -> a.fileKind() == FileKind.TRANSFER_PROOF))
            throw CashCloseExceptions.validationFailed("An unpaid-bill repayment proof is required");
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashMovementResponse> listMovements(UUID branchId, CashMovementListFilter filter, Pageable pageable) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        if (filter.getBranchId() != null && !branchId.equals(filter.getBranchId())) throw CashCloseExceptions.branchHeaderMismatch();
        filter.setBranchId(branchId);
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
    public List<CashMovementResponse> getMovements(UUID branchId, UUID cashCloseId) {
        requireClose(branchId, cashCloseId);
        return movementRepository.findByCashCloseId(cashCloseId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public CashMovementResponse addMovement(UUID branchId, UUID cashCloseId, AddMovementRequest request) {
        CashClose close = requireCloseAtBranch(branchId, cashCloseId, Permission.MOVEMENT_ADD).close();
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
        movement.setSignedAmount(signFor(kind, request.getAmount(), request.getDifferenceDirection()));
        movement.setStaffUserId(request.getStaffUserId());
        movement.setDescription(request.getDescription());
        movement.setReceiptAttachmentId(request.getReceiptAttachmentId());
        movement.setCreatedBy(TenantContext.current().userId());

        validateMovement(kind, movement);

        movement = movementRepository.save(movement);
        entityManager.flush();
        entityManager.refresh(movement);
        return mapper.toResponse(movement, kind);
    }

    @Override
    @Transactional
    public CashMovementResponse updateMovement(UUID branchId, UUID movementId, UpdateMovementRequest request) {
        CashMovement movement = requireMovementAtBranch(branchId, movementId, Permission.MOVEMENT_ADD);
        if (!movement.isEditable()) {
            throw CashCloseExceptions.movementDecided(
                    "Line is %s - reopen it before changing the amount or kind"
                            .formatted(movement.getApprovalStatus()));
        }
        CashClose close = closeRepository.findById(movement.getCashCloseId()).orElseThrow(CashCloseExceptions::cashCloseNotFound);

        MovementKind kind = request.getKindCode() == null
                ? entityManager.find(MovementKind.class, movement.getKindSk())
                : resolveKind(close, request.getKindCode());

        if (request.getAmount() != null || request.getKindCode() != null || request.getDifferenceDirection() != null) {
            BigDecimal amount = request.getAmount() != null
                    ? request.getAmount()
                    : movement.getSignedAmount().abs();
            movement.setKindSk(kind.getKindSk());
            movement.setEffectType(kind.getEffectType());
            DifferenceDirection direction = request.getDifferenceDirection();
            if (direction == null && kind.getEffectType() == EffectType.NO_CASH_FLOW) {
                direction = movement.getSignedAmount().signum() > 0 ? DifferenceDirection.OVER : DifferenceDirection.SHORT;
            }
            movement.setSignedAmount(signFor(kind, amount, direction));
        }
        if (request.getStaffUserId() != null)        movement.setStaffUserId(request.getStaffUserId());
        if (request.getDescription() != null)        movement.setDescription(request.getDescription());
        if (request.getReceiptAttachmentId() != null) movement.setReceiptAttachmentId(request.getReceiptAttachmentId());

        validateMovement(kind, movement);

        return mapper.toResponse(movement, kind);
    }

    @Override
    @Transactional
    public CashMovementResponse approveMovement(UUID branchId, UUID movementId, String note) {
        CashMovement movement = requireMovementAtBranch(branchId, movementId, Permission.MOVEMENT_REVIEW);
        requireMovementTransition(movement, MovementStatus.APPROVED);
        movement.approve(TenantContext.current().userId(), Instant.now(), note);
        return toDto(movement);
    }

    @Override
    @Transactional
    public CashMovementResponse rejectMovement(UUID branchId, UUID movementId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CashCloseExceptions.reasonRequired("Rejecting a line requires a reason");
        }
        CashMovement movement = requireMovementAtBranch(branchId, movementId, Permission.MOVEMENT_REVIEW);
        requireMovementTransition(movement, MovementStatus.REJECTED);
        movement.reject(TenantContext.current().userId(), Instant.now(), reason);
        return toDto(movement);
    }

    @Override
    @Transactional
    public CashMovementResponse reopenMovement(UUID branchId, UUID movementId, String reason) {
        CashMovement movement = requireMovementAtBranch(branchId, movementId, Permission.MOVEMENT_REVIEW);
        requireMovementTransition(movement, MovementStatus.PENDING);
        movement.reopen(reason);
        return toDto(movement);
    }

    // ------------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CloseDecisionResponse> getHistory(UUID branchId, UUID cashCloseId) {
        requireClose(branchId, cashCloseId);
        return mapper.toCloseDecisionList(
                closeDecisionRepository.findByCashCloseIdOrderByActedAtAsc(cashCloseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementDecisionResponse> getMovementHistory(UUID branchId, UUID cashCloseId) {
        requireClose(branchId, cashCloseId);
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
        entityManager.flush();
        // The same view row can already be managed from an earlier read in this
        // transaction. @Synchronize flushes writes but does not refresh that row.
        var calc = calcRepository.findById(close.getCashCloseId()).orElse(null);
        if (calc != null) entityManager.refresh(calc);
        var approval = close.getStatus() == CloseStatus.APPROVED
                ? closeDecisionRepository.findFirstByCashCloseIdAndNewStatusOrderByActedAtDesc(
                    close.getCashCloseId(), CloseStatus.APPROVED)
                : java.util.Optional.<CashCloseDecision>empty();
        return CashCloseView.of(
                close,
                calc,
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
                .resolveAt(normalizeCode(kindCode), close.getBusinessId(), close.getBusinessDate())
                .orElseThrow(() -> CashCloseExceptions.unknownMovementKind(
                        "No movement kind \"%s\" is in force on %s"
                                .formatted(kindCode, close.getBusinessDate())));
    }

    /** Physical cash follows the kind; discrepancy-only lines accept SHORT or OVER. */
    private static BigDecimal signFor(MovementKind kind, BigDecimal positiveAmount, DifferenceDirection direction) {
        if (direction != null && kind.getEffectType() != EffectType.NO_CASH_FLOW) {
            throw CashCloseExceptions.validationFailed("differenceDirection applies only to NO_CASH_FLOW kinds");
        }
        return switch (kind.getEffectType()) {
            case CASH_IN -> positiveAmount;
            case CASH_OUT -> positiveAmount.negate();
            case NO_CASH_FLOW -> direction == DifferenceDirection.OVER ? positiveAmount : positiveAmount.negate();
        };
    }

    private void validateMovement(MovementKind kind, CashMovement movement) {
        if (kind.isRequiresNote() && !hasText(movement.getDescription())) {
            throw CashCloseExceptions.validationFailed("This movement kind requires a description");
        }
        CashClose parent = closeRepository.findById(movement.getCashCloseId()).orElseThrow(CashCloseExceptions::cashCloseNotFound);
        if ("UNPAID_BILL".equals(kind.getKindCode())
                && config.bool(parent.getBranchId(), "REQUIRE_UNPAID_BILL_REPAYMENT", true)) {
            Long proofCount = entityManager.createQuery("""
                    SELECT COUNT(a) FROM CloseAttachment a
                    WHERE a.cashCloseId = :closeId AND a.fileKind = :kind
                    """, Long.class)
                    .setParameter("closeId", movement.getCashCloseId())
                    .setParameter("kind", FileKind.TRANSFER_PROOF)
                    .getSingleResult();
            if (proofCount == 0)
                throw CashCloseExceptions.validationFailed("An unpaid-bill repayment proof is required");
        }
        if ((kind.isRequiresReceipt() || (kind.getExpenseCategory() != null &&
                config.bool(parent.getBranchId(), "REQUIRE_EXPENSE_RECEIPT_IMAGE", false)))
                && movement.getReceiptAttachmentId() == null) {
            throw CashCloseExceptions.validationFailed("This movement kind requires a receipt attachment");
        }
        if (movement.getReceiptAttachmentId() != null) {
            CloseAttachment receipt = entityManager.find(CloseAttachment.class, movement.getReceiptAttachmentId());
            if (receipt == null || !movement.getCashCloseId().equals(receipt.getCashCloseId())
                    || receipt.getFileKind() != FileKind.RECEIPT) {
                throw CashCloseExceptions.validationFailed("Receipt must be attached to this cash close");
            }
        }
        if (movement.getStaffUserId() != null && entityManager.find(com.fnbx.identity.entity.Staff.class, movement.getStaffUserId()) == null) {
            throw CashCloseExceptions.validationFailed("Staff member is unavailable in this business");
        }
    }

    private CashClose requireClose(UUID branchId, UUID id) {
        CashClose close = closeRepository.findById(id).orElseThrow(CashCloseExceptions::cashCloseNotFound);
        branchAccess.require(branchId, Permission.CLOSE_READ);
        if (!branchId.equals(close.getBranchId())) throw CashCloseExceptions.branchHeaderMismatch();
        return close;
    }

    private Scoped requireCloseAtBranch(UUID branchId, UUID cashCloseId, Permission permission) {
        branchAccess.require(branchId, permission);
        CashClose close = closeRepository.findById(cashCloseId).orElseThrow(CashCloseExceptions::cashCloseNotFound);
        if (!close.getBranchId().equals(branchId)) throw CashCloseExceptions.branchHeaderMismatch();
        entityManager.refresh(close, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return new Scoped(close, permission);
    }

    private record Scoped(CashClose close, Permission permission) {}

    /** Currency and local date come from the authenticated tenant. */
    private Business business() {
        Business business = entityManager.find(Business.class, TenantContext.current().businessId());
        if (business == null) throw CashCloseExceptions.validationFailed("Business is unavailable");
        return business;
    }

    /** Face value to denomination, compared by value so 500000 and 500000.00 agree. */
    private NavigableMap<BigDecimal, Denomination> countableDenominations() {
        NavigableMap<BigDecimal, Denomination> byFaceValue = new TreeMap<>();
        denominationRepository.findByCurrencyCodeAndActiveTrue(business().getCurrencyCode())
                .forEach(d -> byFaceValue.put(d.getFaceValue(), d));
        return byFaceValue;
    }

    /**
     * The stored count, rendered with face values and the total.
     *
     * <p>Reads EVERY denomination, not just the active ones: a note taken out of
     * circulation is deactivated rather than deleted, and a close counted before that
     * day must still render the line it holds.
     *
     * <p>{@code countedCash} comes from {@code v_close_calc}. Summing the lines here
     * instead would be a second implementation of counted cash living one method away
     * from the first - which is how the 16 disagreeing totals in the old data
     * happened, and why the column was removed.
     */
    private DenominationSetResponse denominationSet(CashClose close) {
        Map<Short, BigDecimal> faceValues = new HashMap<>();
        denominationRepository.findByCurrencyCode(business().getCurrencyCode())
                .forEach(d -> faceValues.put(d.getDenominationId(), d.getFaceValue()));

        List<DenominationLineResponse> lines =
                denominationLineRepository.findByCashCloseId(close.getCashCloseId()).stream()
                        .map(line -> {
                            BigDecimal faceValue = faceValues.get(line.getDenominationId());
                            return DenominationLineResponse.builder()
                                    .lineId(line.getLineId())
                                    .faceValue(faceValue)
                                    .quantity(line.getQuantity())
                                    .lineTotal(faceValue == null ? null
                                            : faceValue.multiply(BigDecimal.valueOf(line.getQuantity())))
                                    .build();
                        })
                        .sorted((a, b) -> compareFaceValueDesc(a.getFaceValue(), b.getFaceValue()))
                        .toList();

        return DenominationSetResponse.builder()
                .cashCloseId(close.getCashCloseId())
                .status(close.getStatus().name())
                .lines(lines)
                .countedCash(nz(calcRepository.findById(close.getCashCloseId())
                        .map(CashCloseCalc::getCountedCash)
                        .orElse(null)))
                .build();
    }

    /** Largest note first - the order cash is counted in, and displayed in. */
    private static int compareFaceValueDesc(BigDecimal a, BigDecimal b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    private CashMovement requireMovementAtBranch(UUID branchId, UUID id, Permission permission) {
        branchAccess.require(branchId, permission);
        CashMovement movement = movementRepository.findById(id).orElseThrow(CashCloseExceptions::movementNotFound);
        CashClose close = requireCloseAtBranch(branchId, movement.getCashCloseId(), permission).close();
        if (!close.isEditable()) throw CashCloseExceptions.closeFrozen("Close is frozen");
        entityManager.refresh(movement);
        return movement;
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
                                CloseStatus from, CloseStatus to, String note, Permission actedPermission) {
        recordDecision(close, action, from, to, note, actedPermission, null);
    }

    private void recordDecision(CashClose close, ApprovalAction action,
                                CloseStatus from, CloseStatus to, String note,
                                Permission actedPermission, Map<String, Object> changes) {
        CashCloseDecision decision = new CashCloseDecision();
        decision.setDecisionId(UUID.randomUUID());
        decision.setCashCloseId(close.getCashCloseId());
        decision.setBusinessId(close.getBusinessId());
        decision.setAction(action);
        decision.setActedBy(TenantContext.current().userId());
        decision.setActedPermission(actedPermission.name());
        // Snapshot the active position that grants this permission at this branch.
        // Direct grants have no position to attribute, so they remain null.
        List<?> positions = entityManager.createNativeQuery("""
                SELECT p.position_code FROM identity.staff_branch_position a
                JOIN identity.staff_position p ON p.position_id=a.position_id AND p.business_id=a.business_id
                JOIN identity.position_permission g ON g.position_id=p.position_id AND g.business_id=p.business_id
                WHERE a.staff_id=:staff AND a.branch_id=:branch AND a.business_id=:business
                  AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
                  AND p.is_active AND g.permission_code=:permission
                  AND g.revoked_at IS NULL AND g.granted_at<=clock_timestamp()
                ORDER BY p.position_code LIMIT 1
                """).setParameter("staff", TenantContext.current().userId())
                .setParameter("branch", close.getBranchId())
                .setParameter("business", close.getBusinessId())
                .setParameter("permission", actedPermission.name()).getResultList();
        if (!positions.isEmpty()) decision.setActedPosition((String) positions.getFirst());
        decision.setChanges(changes);
        decision.setOldStatus(from);
        decision.setNewStatus(to);
        decision.setNote(note);
        decision = closeDecisionRepository.save(decision);
        entityManager.flush();
        entityManager.refresh(decision);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Specification<CashClose> cashCloseFilter(
            CashCloseListFilter filter,
            CloseStatus status,
            ExpectedCashSource expectedCashSource) {
        var readableBranches = branchAccess.branches(Permission.CLOSE_READ);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("branchId").in(readableBranches));
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

    private Specification<CashMovement> movementFilter(
            CashMovementListFilter filter,
            MovementStatus approvalStatus,
            EffectType effectType) {
        var readableBranches = branchAccess.branches(Permission.CLOSE_READ);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            assert query != null;
            var closeRoot = query.from(CashClose.class);
            predicates.add(closeRoot.get("branchId").in(readableBranches));
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

    private static String buildCode(UUID branchId, SubmitCashCloseRequest r) {
        return "CC-%s-%s-%s-%s".formatted(
                r.getBusinessDate(), shortId(branchId), shortId(r.getShiftTypeId()), UUID.randomUUID());
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }
}

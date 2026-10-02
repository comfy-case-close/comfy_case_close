package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.request.*;
import com.fnbx.cashclose.dto.response.FundWithdrawalResponse;
import com.fnbx.cashclose.dto.response.FundWithdrawalDecisionResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.FundWithdrawal;
import com.fnbx.cashclose.entity.FundWithdrawalDecision;
import com.fnbx.cashclose.enums.FundWithdrawalAction;
import com.fnbx.cashclose.repository.FundWithdrawalDecisionRepository;
import com.fnbx.cashclose.enums.CashPot;
import com.fnbx.cashclose.enums.FundStatus;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.repository.FundWithdrawalRepository;
import com.fnbx.cashclose.service.FundWithdrawalService;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FundWithdrawalServiceImpl implements FundWithdrawalService {
    private final FundWithdrawalRepository withdrawals;
    private final FundWithdrawalDecisionRepository decisions;
    private final BranchAccessGuard branchAccess;
    private final EntityManager entityManager;
    private final com.fnbx.cashclose.service.EffectiveConfig config;

    @Override @Transactional(readOnly = true)
    public Page<FundWithdrawalResponse> list(UUID branchId, UUID cashCloseId,
            LocalDate fromDate, LocalDate toDate, FundStatus status, Pageable pageable) {
        branch(branchId);
        boolean finance = branchAccess.effective(branchId).contains(Permission.FINANCE_READ);
        if (!finance) branchAccess.require(branchId,
                cashCloseId == null ? Permission.WITHDRAWAL_RECORD : Permission.CLOSE_READ);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw CashCloseExceptions.invalidFilter("fromDate must be before or equal to toDate");
        Business business = entityManager.find(Business.class, TenantContext.current().businessId());
        ZoneId zone = ZoneId.of(business.getTimezone());
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return withdrawals.findAll((root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("businessId"), TenantContext.current().businessId()));
            p.add(cb.equal(root.get("branchId"), branchId));
            if (cashCloseId != null) p.add(cb.equal(root.get("cashCloseId"), cashCloseId));
            if (!finance && cashCloseId == null) p.add(cb.equal(root.get("withdrawnBy"), TenantContext.current().userId()));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("withdrawnAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("withdrawnAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        }, pageable).map(FundWithdrawalResponse::from);
    }

    @Override @Transactional(readOnly = true)
    public FundWithdrawalResponse get(UUID branchId, UUID id) {
        FundWithdrawal w = scoped(branchId, id);
        Set<Permission> permissions = branchAccess.effective(branchId);
        if (!permissions.contains(Permission.FINANCE_READ)
                && !(w.getCashCloseId() != null && permissions.contains(Permission.CLOSE_READ))) {
            branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
            requireNamedPerson(w);
        }
        return FundWithdrawalResponse.from(w);
    }

    @Override @Transactional(readOnly = true)
    public List<FundWithdrawalDecisionResponse> history(UUID branchId, UUID id) {
        get(branchId, id); // same tenant, branch and reader authorization as the withdrawal
        return decisions.findByFundWithdrawalIdAndBusinessIdOrderByActedAtAsc(
                id, TenantContext.current().businessId()).stream().map(FundWithdrawalDecisionResponse::from).toList();
    }

    @Override
    public FundWithdrawalResponse record(UUID branchId, FundWithdrawalRequest request) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        branch(branchId);
        if (request.getFromPot() == null || request.getToPot() == null || request.getFromPot() == request.getToPot())
            throw CashCloseExceptions.validationFailed("Choose two different cash pots");
        if (request.getAmount() == null || request.getAmount().signum() <= 0)
            throw CashCloseExceptions.validationFailed("Transfer amount must be positive");
        validateAmount(request.getAmount());
        validatePerson(branchId, request.getWithdrawnBy(), request.getWithdrawnAt());
        return recorded(saveDeclaration(branchId, null, request.getFromPot(), request.getToPot(),
                request.getAmount(), request.getWithdrawnBy(), request.getWithdrawnAt(), null, null, request.getNote()));
    }

    @Override
    public FundWithdrawalResponse correct(UUID branchId, UUID id, CorrectFundWithdrawalRequest request) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        if (request.getEditReason() == null || request.getEditReason().isBlank())
            throw CashCloseExceptions.reasonRequired("A withdrawal correction needs a reason");
        FundWithdrawal old = locked(branchId, id);
        CashClose close = editableLinkedClose(old);
        validateAmount(request.getAmount());
        validatePerson(branchId, request.getWithdrawnBy(), request.getWithdrawnAt());
        if (close != null) close.setWithdrawalAmount(request.getAmount());
        return recorded(saveDeclaration(branchId, old.getCashCloseId(), old.getFromPot(), old.getToPot(),
                request.getAmount(), request.getWithdrawnBy(), request.getWithdrawnAt(), old,
                request.getEditReason(), request.getNote()));
    }

    @Override
    public FundWithdrawalResponse confirm(UUID branchId, UUID id) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        FundWithdrawal w = locked(branchId, id);
        requireNamedPerson(w);
        if (w.getStatus() == FundStatus.CONFIRMED) return FundWithdrawalResponse.from(w);
        requirePending(w);
        return decide(w, FundWithdrawalAction.CONFIRM, FundStatus.CONFIRMED, null, null);
    }

    @Override
    public FundWithdrawalResponse reject(UUID branchId, UUID id, String reason) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        if (reason == null || reason.isBlank()) throw CashCloseExceptions.reasonRequired("A rejection needs a reason");
        FundWithdrawal w = locked(branchId, id);
        requireNamedPerson(w);
        requirePending(w);
        return decide(w, FundWithdrawalAction.REJECT, FundStatus.REJECTED, reason.trim(), null);
    }

    private FundWithdrawalResponse decide(FundWithdrawal w, FundWithdrawalAction action, FundStatus next, String note,
            Map<String, Object> changes) {
        FundWithdrawalDecision decision = new FundWithdrawalDecision();
        decision.setDecisionId(UUID.randomUUID());
        decision.setFundWithdrawalId(w.getFundWithdrawalId());
        decision.setBusinessId(w.getBusinessId());
        decision.setAction(action);
        decision.setActedBy(TenantContext.current().userId());
        decision.setOldStatus(w.getStatus());
        decision.setNewStatus(next);
        decision.setNote(note);
        decision.setChanges(changes == null ? Map.of() : changes);
        // The decision INSERT applies the state change atomically in PostgreSQL.
        decisions.saveAndFlush(decision);
        entityManager.refresh(w);
        return FundWithdrawalResponse.from(w);
    }

    @Override
    public Map<String, Object> describeCloseCorrection(CashClose close, CashCloseFiguresRequest figures) {
        if (!hasWithdrawalInput(figures)) return Map.of();
        FundWithdrawal old = current(close);
        Declaration next = declaration(close, figures, old);
        if (!changed(old, next)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("before", old == null ? null : snapshot(old.getAmount(), old.getWithdrawnBy(), old.getWithdrawnAt()));
        result.put("after", snapshot(next.amount(), next.person(), next.time()));
        return result;
    }

    /** Caller holds the close row lock, or is creating the close in this transaction. */
    @Override
    public void syncCloseWithdrawal(CashClose close, CashCloseFiguresRequest figures, String editReason) {
        if (!hasWithdrawalInput(figures)) return;
        FundWithdrawal old = current(close);
        Declaration next = declaration(close, figures, old);
        if (!changed(old, next)) return;
        branchAccess.require(close.getBranchId(), Permission.WITHDRAWAL_RECORD);
        validatePerson(close.getBranchId(), next.person(), next.time());
        saveDeclaration(close.getBranchId(), close.getCashCloseId(), CashPot.DRAWER, CashPot.BRANCH_SAFE,
                next.amount(), next.person(), next.time(), old, editReason, null);
    }

    @Override
    public void requireConfirmed(CashClose close) {
        FundWithdrawal current = current(close);
        if (current == null && close.getWithdrawalAmount().signum() == 0) return;
        if (current == null || current.getStatus() != FundStatus.CONFIRMED
                || current.getAmount().compareTo(close.getWithdrawalAmount()) != 0)
            throw new AppException(ErrorCode.WITHDRAWAL_CONFIRMATION_REQUIRED);
    }

    private FundWithdrawal current(CashClose close) {
        return withdrawals.findByCashCloseId(close.getCashCloseId()).orElse(null);
    }

    private record Declaration(BigDecimal amount, UUID person, Instant time) {}
    private Declaration declaration(CashClose close, CashCloseFiguresRequest f, FundWithdrawal old) {
        BigDecimal amount = f.getWithdrawalAmount() == null ? close.getWithdrawalAmount() : f.getWithdrawalAmount();
        validateAmount(amount);
        UUID person = f.getWithdrawnBy() != null ? f.getWithdrawnBy() : old == null ? null : old.getWithdrawnBy();
        Instant time = f.getWithdrawnAt() != null ? f.getWithdrawnAt().truncatedTo(ChronoUnit.MICROS)
                : old == null ? null : old.getWithdrawnAt();
        if (old == null && amount.signum() == 0 && (person != null || time != null))
            throw CashCloseExceptions.validationFailed("Withdrawal attribution requires a positive amount");
        return new Declaration(amount, person, time);
    }

    private boolean changed(FundWithdrawal old, Declaration next) {
        if (old == null) return next.amount().signum() > 0;
        return old.getStatus() == FundStatus.REJECTED || old.getAmount().compareTo(next.amount()) != 0
                || !Objects.equals(old.getWithdrawnBy(), next.person()) || !Objects.equals(old.getWithdrawnAt(), next.time());
    }

    private static boolean hasWithdrawalInput(CashCloseFiguresRequest f) {
        return f != null && (f.getWithdrawalAmount() != null || f.getWithdrawnBy() != null || f.getWithdrawnAt() != null);
    }

    private static Map<String, Object> snapshot(BigDecimal amount, UUID person, Instant time) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount); data.put("withdrawnBy", person == null ? null : person.toString()); data.put("withdrawnAt", time == null ? null : time.toString());
        return data;
    }

    private FundWithdrawal saveDeclaration(UUID branchId, UUID closeId, CashPot from, CashPot to, BigDecimal amount,
            UUID person, Instant time, FundWithdrawal existing, String editReason, String note) {
        time = time.truncatedTo(ChronoUnit.MICROS);
        if (existing != null) {
            if (editReason == null || editReason.isBlank())
                throw CashCloseExceptions.reasonRequired("A withdrawal correction needs a reason");
            String nextNote = closeId != null && note == null ? existing.getNote() : note;
            Map<String, Object> before = snapshot(existing.getAmount(), existing.getWithdrawnBy(), existing.getWithdrawnAt());
            before.put("note", existing.getNote());
            Map<String, Object> after = snapshot(amount, person, time);
            after.put("note", nextNote);
            decide(existing, FundWithdrawalAction.EDIT, FundStatus.PENDING, editReason.trim(),
                    Map.of("before", before, "after", after));
            return existing;
        }
        FundWithdrawal w = new FundWithdrawal();
        w.setFundWithdrawalId(UUID.randomUUID());
        w.setBusinessId(TenantContext.current().businessId());
        w.setBranchId(branchId); w.setCashCloseId(closeId);
        w.setFromPot(from); w.setToPot(to); w.setAmount(amount);
        w.setWithdrawnBy(person); w.setWithdrawnAt(time);
        w.setRecordedBy(TenantContext.current().userId()); w.setNote(note);
        return withdrawals.saveAndFlush(w);
    }

    private FundWithdrawalResponse recorded(FundWithdrawal w) {
        BigDecimal threshold = config.number(w.getBranchId(), "FUND_WITHDRAWAL_WARNING_ABS", BigDecimal.ZERO);
        var response = FundWithdrawalResponse.from(w);
        if (threshold.signum() > 0 && w.getAmount().compareTo(threshold) > 0)
            return response.withWarnings(List.of(new FundWithdrawalResponse.Warning(
                    "WITHDRAW_OVER_WARNING_THRESHOLD", w.getAmount(), threshold)));
        return response;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw CashCloseExceptions.validationFailed("Invalid withdrawal amount");
    }

    private void validatePerson(UUID branchId, UUID person, Instant time) {
        if (person == null || time == null || time.isAfter(Instant.now()))
            throw CashCloseExceptions.validationFailed("withdrawnBy and a non-future withdrawnAt are required");
        Number count = (Number) entityManager.createNativeQuery("""
            SELECT count(*) FROM identity.staff s
            WHERE s.staff_id=:staff AND s.business_id=:business AND s.is_active AND (
              EXISTS (SELECT 1 FROM identity.staff_branch_position a
                JOIN identity.position p ON p.position_id=a.position_id AND p.business_id=a.business_id AND p.is_active
                JOIN identity.position_permission g ON g.position_id=p.position_id AND g.business_id=p.business_id
                WHERE a.staff_id=s.staff_id AND a.business_id=s.business_id AND a.branch_id=:branch
                AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
                AND g.permission_code='WITHDRAWAL_RECORD' AND g.revoked_at IS NULL AND g.granted_at<=clock_timestamp()))
            """).setParameter("staff", person).setParameter("business", TenantContext.current().businessId())
                .setParameter("branch", branchId).getSingleResult();
        if (count.longValue() == 0)
            throw CashCloseExceptions.validationFailed("The withdrawing person needs active withdrawal permission at this branch");
    }

    private FundWithdrawal scoped(UUID branchId, UUID id) {
        branch(branchId);
        FundWithdrawal w = withdrawals.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!w.getBusinessId().equals(TenantContext.current().businessId()) || !w.getBranchId().equals(branchId))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return w;
    }

    private FundWithdrawal locked(UUID branchId, UUID id) {
        FundWithdrawal w = scoped(branchId, id);
        // Same lock order as close correction/approval: close first, withdrawal second.
        if (w.getCashCloseId() != null) {
            CashClose close = entityManager.find(CashClose.class, w.getCashCloseId());
            entityManager.refresh(close, LockModeType.PESSIMISTIC_WRITE);
        }
        entityManager.refresh(w, LockModeType.PESSIMISTIC_WRITE);
        return w;
    }
    private CashClose editableLinkedClose(FundWithdrawal w) {
        if (w.getCashCloseId() == null) return null;
        CashClose close = entityManager.find(CashClose.class, w.getCashCloseId());
        if (close == null || !close.getBusinessId().equals(TenantContext.current().businessId())
                || !close.getBranchId().equals(w.getBranchId()))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!close.isEditable()) throw CashCloseExceptions.closeFrozen("Close is frozen");
        return close;
    }

    private void requirePending(FundWithdrawal w) {
        if (w.getStatus() != FundStatus.PENDING)
            throw new AppException(ErrorCode.RESOURCE_CONFLICT, "Only a pending withdrawal can be decided");
    }
    private void requireNamedPerson(FundWithdrawal w) {
        if (!w.getWithdrawnBy().equals(TenantContext.current().userId()))
            throw new AccessDeniedException("Only the named withdrawing person can confirm or reject this withdrawal");
    }
    private Branch branch(UUID branchId) {
        Branch branch = entityManager.find(Branch.class, branchId);
        if (branch == null || !branch.isActive() || !branch.getBusinessId().equals(TenantContext.current().businessId()))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return branch;
    }
}

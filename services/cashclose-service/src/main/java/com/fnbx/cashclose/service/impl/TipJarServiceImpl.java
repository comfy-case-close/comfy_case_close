package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.request.TipPayoutRequest;
import com.fnbx.cashclose.dto.response.TipJarResponse;
import com.fnbx.cashclose.dto.response.TipPayoutResultResponse;
import com.fnbx.cashclose.entity.TipPayout;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.TipPayoutRepository;
import com.fnbx.cashclose.service.TipJarService;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.identity.entity.Staff;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TipJarServiceImpl implements TipJarService {
    private static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);
    private static final String POOLED_TIP_KIND = "TIP_JAR";
    private static final String DRAWER_TIP_KIND = "TIP_IN_DRAWER";

    private final TipPayoutRepository payouts;
    private final CashMovementRepository movements;
    private final BranchAccessGuard branchAccess;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public TipJarResponse getTipJar(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        branchAccess.require(branchId, Permission.FINANCE_READ);
        Branch branch = branch(branchId);
        LocalDate today = today();
        LocalDate from = fromDate == null ? today.withDayOfMonth(1) : fromDate;
        LocalDate to = toDate == null ? today : toDate;
        requireDateRange(from, to, today);

        List<TipPayout> inRange = payouts.findByBranchIdAndPayoutDateBetweenOrderByPayoutDateDescCreatedAtDesc(
                branchId, from, to);
        BigDecimal paidOut = payouts.sumInRange(branchId, from, to);
        return new TipJarResponse(
                new TipJarResponse.Scope(branchId, branch.getBranchCode(), branch.getBranchName(), from, to),
                new TipJarResponse.Summary(
                        tipTotal(branchId, from, to, POOLED_TIP_KIND),
                        tipTotal(branchId, from, to, DRAWER_TIP_KIND),
                        paidOut, balance(branchId, to), inRange.size()),
                inRange.stream().map(p -> response(p, branch)).toList());
    }

    @Override
    @Transactional
    public TipPayoutResultResponse recordPayout(UUID branchId, TipPayoutRequest request) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        Branch branch = branch(branchId);
        LocalDate today = today();
        LocalDate date = request.getPayoutDate() == null ? today : request.getPayoutDate();
        if (date.isAfter(today)) {
            throw CashCloseExceptions.validationFailed("payoutDate cannot be in the future");
        }
        BigDecimal balanceBefore = balance(branchId, date);

        TipPayout payout = new TipPayout();
        payout.setTipPayoutId(UUID.randomUUID());
        payout.setBusinessId(TenantContext.current().businessId());
        payout.setBranchId(branchId);
        payout.setAmount(request.getAmount());
        payout.setPayoutDate(date);
        payout.setRecipientName(trimmedOrNull(request.getRecipientName()));
        payout.setNote(trimmedOrNull(request.getNote()));
        payout.setCreatedBy(TenantContext.current().userId());
        payouts.saveAndFlush(payout);
        entityManager.refresh(payout);

        List<TipPayoutResultResponse.Warning> warnings = request.getAmount().compareTo(balanceBefore) > 0
                ? List.of(new TipPayoutResultResponse.Warning(
                        "PAYOUT_EXCEEDS_JAR_BALANCE", request.getAmount(), balanceBefore.max(BigDecimal.ZERO)))
                : List.of();
        return new TipPayoutResultResponse(
                response(payout, branch), balanceBefore.subtract(request.getAmount()), warnings);
    }

    private BigDecimal balance(UUID branchId, LocalDate asOf) {
        return tipTotal(branchId, BEGINNING, asOf, POOLED_TIP_KIND)
                .subtract(payouts.sumInRange(branchId, BEGINNING, asOf));
    }

    private BigDecimal tipTotal(UUID branchId, LocalDate from, LocalDate to, String kindCode) {
        return movements.sumTipsByKind(branchId, from, to, kindCode);
    }

    private Branch branch(UUID branchId) {
        Branch branch = entityManager.find(Branch.class, branchId);
        if (branch == null || !branch.isActive()
                || !branch.getBusinessId().equals(TenantContext.current().businessId())) {
            throw CashCloseExceptions.validationFailed("Branch is unavailable");
        }
        return branch;
    }

    private LocalDate today() {
        Business business = entityManager.find(Business.class, TenantContext.current().businessId());
        if (business == null) throw CashCloseExceptions.validationFailed("Business is unavailable");
        return LocalDate.now(ZoneId.of(business.getTimezone()));
    }

    private static void requireDateRange(LocalDate from, LocalDate to, LocalDate today) {
        if (from.isAfter(to) || from.isAfter(today) || to.isAfter(today)) {
            throw CashCloseExceptions.invalidFilter("Tip jar date range must be ordered and not in the future");
        }
    }

    private TipJarResponse.Payout response(TipPayout payout, Branch branch) {
        Staff creator = entityManager.find(Staff.class, payout.getCreatedBy());
        String creatorName = creator == null ? null :
                (creator.getFirstName() + " " + creator.getLastName()).trim();
        return new TipJarResponse.Payout(
                payout.getTipPayoutId(), payout.getBranchId(), branch.getBranchCode(),
                branch.getBranchName(), payout.getAmount(), payout.getPayoutDate(),
                payout.getRecipientName(), payout.getNote(), payout.getCreatedBy(),
                creatorName, payout.getCreatedAt());
    }

    private static String trimmedOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}

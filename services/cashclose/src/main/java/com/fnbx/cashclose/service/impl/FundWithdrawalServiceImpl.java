package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.request.FundWithdrawalRequest;
import com.fnbx.cashclose.dto.response.FundWithdrawalPotResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.FundWithdrawal;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.FundPeriod;
import com.fnbx.cashclose.enums.FundStatus;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adapts dev's fund pot and non-blocking warnings to the versioned close DDL. */
@Service
@RequiredArgsConstructor
public class FundWithdrawalServiceImpl implements FundWithdrawalService {
    private static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);

    private final FundWithdrawalRepository withdrawals;
    private final CashCloseRepository closes;
    private final CashCloseCalcRepository calculations;
    private final BranchAccessGuard branchAccess;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public FundWithdrawalPotResponse getPot(UUID branchId, LocalDate fromDate,
                                             LocalDate toDate, FundPeriod periodType) {
        branchAccess.require(branchId, Permission.FINANCE_READ);
        Branch branch = branch(branchId);
        DateRange range = dateRange(fromDate, toDate);
        return pot(branch, range, periodType == null ? FundPeriod.ADHOC : periodType, List.of());
    }

    @Override
    @Transactional
    public FundWithdrawalPotResponse record(UUID branchId, FundWithdrawalRequest request) {
        branchAccess.require(branchId, Permission.WITHDRAWAL_RECORD);
        Branch branch = branch(branchId);
        DateRange range = dateRange(request.getFromDate(), request.getToDate());
        if (!withdrawals.findLiveOverlapping(branchId, range.from(), range.to()).isEmpty()) {
            throw new AppException(ErrorCode.RESOURCE_CONFLICT,
                    "A live fund withdrawal already covers some of this period");
        }

        BigDecimal before = availableThrough(branchId, range.to());
        BigDecimal systemAmount = request.getSystemWithdrawAmount();
        BigDecimal actualAmount = request.getActualReceivedAmount() == null
                ? systemAmount : request.getActualReceivedAmount();

        FundWithdrawal withdrawal = new FundWithdrawal();
        withdrawal.setFundWithdrawalId(UUID.randomUUID());
        withdrawal.setFundWithdrawalCode("FW-" + withdrawal.getFundWithdrawalId());
        withdrawal.setBusinessId(TenantContext.current().businessId());
        withdrawal.setBranchId(branchId);
        withdrawal.setPeriodType(request.getPeriodType() == null ? FundPeriod.ADHOC : request.getPeriodType());
        withdrawal.setPeriodFrom(range.from());
        withdrawal.setPeriodTo(range.to());
        withdrawal.setCreatedBy(TenantContext.current().userId());
        withdrawal.setSystemPotBefore(before);
        withdrawal.setSystemWithdrawAmount(systemAmount);
        withdrawal.setActualReceivedAmount(actualAmount);
        withdrawal.setSystemPotAfter(before.subtract(systemAmount));
        withdrawal.setStatus(FundStatus.CLOSED);
        withdrawal.setNote(request.getNote() == null || request.getNote().isBlank()
                ? null : request.getNote().trim());
        withdrawals.saveAndFlush(withdrawal);
        entityManager.refresh(withdrawal); // generated variance and timestamps

        List<FundWithdrawalPotResponse.Warning> warnings = new ArrayList<>();
        if (systemAmount.compareTo(before) > 0) {
            warnings.add(new FundWithdrawalPotResponse.Warning(
                    "WITHDRAW_EXCEEDS_REMAINING_POT", systemAmount, before.max(BigDecimal.ZERO)));
        }
        BigDecimal threshold = warningThreshold(branchId);
        if (threshold.signum() > 0 && systemAmount.compareTo(threshold) > 0) {
            warnings.add(new FundWithdrawalPotResponse.Warning(
                    "WITHDRAW_OVER_WARNING_THRESHOLD", systemAmount, threshold));
        }
        return pot(branch, range, withdrawal.getPeriodType(), warnings);
    }

    private FundWithdrawalPotResponse pot(Branch branch, DateRange range, FundPeriod periodType,
                                          List<FundWithdrawalPotResponse.Warning> warnings) {
        UUID branchId = branch.getBranchId();
        List<CashClose> sourceCloses = closes
                .findByBranchIdAndBusinessDateBetweenAndStatusOrderByBusinessDateDesc(
                        branchId, range.from(), range.to(), CloseStatus.APPROVED).stream()
                .filter(c -> c.getWithdrawalAmount().signum() > 0).toList();
        List<FundWithdrawal> logs = withdrawals.findLiveOverlapping(branchId, range.from(), range.to());
        BigDecimal generated = sourceCloses.stream().map(CashClose::getWithdrawalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawn = logs.stream().map(FundWithdrawal::getSystemWithdrawAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = logs.stream().map(FundWithdrawal::getActualReceivedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = logs.stream().map(FundWithdrawal::getVarianceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FundWithdrawalPotResponse(
                new FundWithdrawalPotResponse.Scope(branchId, branch.getBranchCode(),
                        branch.getBranchName(), range.from(), range.to(), periodType),
                new FundWithdrawalPotResponse.Summary(generated, withdrawn, actual, variance,
                        generated.subtract(withdrawn), sourceCloses.size(), logs.size()),
                sourceCloses.stream().map(this::source).toList(),
                logs.stream().map(this::log).toList(), warnings);
    }

    private FundWithdrawalPotResponse.Source source(CashClose close) {
        CashCloseCalc calc = calculations.findById(close.getCashCloseId()).orElse(null);
        return new FundWithdrawalPotResponse.Source(close.getCashCloseId(), close.getCashCloseCode(),
                close.getBusinessDate(), close.getWithdrawalAmount(),
                calc == null ? null : calc.getCashRemaining());
    }

    private FundWithdrawalPotResponse.Log log(FundWithdrawal w) {
        return new FundWithdrawalPotResponse.Log(w.getFundWithdrawalId(), w.getFundWithdrawalCode(),
                w.getPeriodFrom(), w.getPeriodTo(), w.getPeriodType(), w.getSystemPotBefore(),
                w.getSystemWithdrawAmount(), w.getActualReceivedAmount(), w.getVarianceAmount(),
                w.getSystemPotAfter(), w.getNote(), w.getStatus(), w.getCreatedBy(), w.getCreatedAt());
    }

    private BigDecimal availableThrough(UUID branchId, LocalDate asOf) {
        return withdrawals.sumApprovedWithdrawals(branchId, BEGINNING, asOf)
                .subtract(withdrawals.sumLiveThrough(branchId, asOf));
    }

    private BigDecimal warningThreshold(UUID branchId) {
        Object value = entityManager.createNativeQuery(
                        "SELECT platform.fn_config_num(:branchId, 'FUND_WITHDRAWAL_WARNING_ABS')")
                .setParameter("branchId", branchId).getSingleResult();
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private DateRange dateRange(LocalDate fromDate, LocalDate toDate) {
        Business business = entityManager.find(Business.class, TenantContext.current().businessId());
        if (business == null) throw CashCloseExceptions.validationFailed("Business is unavailable");
        LocalDate today = LocalDate.now(ZoneId.of(business.getTimezone()));
        LocalDate from = fromDate == null ? today.withDayOfMonth(1) : fromDate;
        LocalDate to = toDate == null ? today : toDate;
        if (from.isAfter(to) || from.isAfter(today) || to.isAfter(today)) {
            throw CashCloseExceptions.invalidFilter("Withdrawal date range must be ordered and not in the future");
        }
        return new DateRange(from, to);
    }

    private Branch branch(UUID branchId) {
        Branch branch = entityManager.find(Branch.class, branchId);
        if (branch == null || !branch.isActive()
                || !branch.getBusinessId().equals(TenantContext.current().businessId())) {
            throw CashCloseExceptions.validationFailed("Branch is unavailable");
        }
        return branch;
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}

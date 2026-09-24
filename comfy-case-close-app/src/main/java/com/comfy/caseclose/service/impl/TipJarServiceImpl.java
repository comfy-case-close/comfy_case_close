package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.TipPayoutRequest;
import com.comfy.caseclose.dto.response.TipJarDTO;
import com.comfy.caseclose.dto.response.TipPayoutResultDTO;
import com.comfy.caseclose.dto.response.WarningDTO;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.TipPayout;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.TipPayoutRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.TipJarService;
import com.comfy.caseclose.utils.BusinessDates;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TipJarServiceImpl implements TipJarService {

    private static final List<CashCloseStatus> EXCLUDED_STATUSES =
            List.of(CashCloseStatus.REJECTED, CashCloseStatus.VOIDED);
    private static final String ALL_BRANCHES_LABEL = "Tất cả chi nhánh";
    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    private final TipRepository tipRepository;
    private final TipPayoutRepository tipPayoutRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public TipJarDTO getTipJar(Long branchId, LocalDate fromDate, LocalDate toDate) {
        BusinessDates.requireNotInFuture(fromDate, "fromDate");
        BusinessDates.requireNotInFuture(toDate, "toDate");
        LocalDate from = fromDate != null ? fromDate : BusinessDates.today().withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : BusinessDates.today();
        if (from.isAfter(to)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }
        Branch branch = branchId == null ? null : resolveBranch(branchId);

        List<TipPayout> payouts = tipPayoutRepository.findInRange(from, to, branchId);
        return TipJarDTO.builder()
                .scope(TipJarDTO.Scope.builder()
                        .branchId(branchId)
                        .branchCode(branch == null ? null : branch.getBranchCode())
                        .branchLabel(branch == null ? ALL_BRANCHES_LABEL : branch.getBranchName())
                        .fromDate(from)
                        .toDate(to)
                        .build())
                .summary(TipJarDTO.Summary.builder()
                        .tipsIn(tipRepository.sumByFlow(false, from, to, EXCLUDED_STATUSES, branchId))
                        .tipsInsideDrawer(tipRepository.sumByFlow(true, from, to, EXCLUDED_STATUSES, branchId))
                        .paidOut(payouts.stream().mapToLong(TipPayout::getAmount).sum())
                        .balance(balance(branchId, to))
                        .payoutCount(payouts.size())
                        .build())
                .payouts(payouts.stream().map(this::toPayoutDTO).toList())
                .build();
    }

    @Override
    @Transactional
    public TipPayoutResultDTO recordPayout(TipPayoutRequest request) {
        Branch branch = resolveBranch(request.getBranchId());
        BusinessDates.requireNotInFuture(request.getPayoutDate(), "Payout date");
        LocalDate payoutDate = request.getPayoutDate() != null ? request.getPayoutDate() : BusinessDates.today();
        long amount = request.getAmount();

        long balanceBefore = balance(branch.getId(), payoutDate);

        TipPayout payout = new TipPayout();
        payout.setBranch(branch);
        payout.setAmount(amount);
        payout.setPayoutDate(payoutDate);
        payout.setRecipientName(InputNormalizer.text(request.getRecipientName()));
        payout.setNote(InputNormalizer.text(request.getNote()));
        payout.setCreatedBy(currentUser());
        tipPayoutRepository.save(payout);

        List<WarningDTO> warnings = amount > balanceBefore
                ? List.of(WarningDTO.builder()
                        .code(WarningDTO.PAYOUT_EXCEEDS_JAR_BALANCE)
                        .amount(amount)
                        .limit(Math.max(balanceBefore, 0))
                        .build())
                : List.of();

        return TipPayoutResultDTO.builder()
                .payout(toPayoutDTO(payout))
                .balanceAfter(balanceBefore - amount)
                .warnings(warnings)
                .build();
    }

    private long balance(Long branchId, LocalDate asOf) {
        return tipRepository.sumByFlow(false, BEGINNING, asOf, EXCLUDED_STATUSES, branchId)
                - tipPayoutRepository.sumInRange(BEGINNING, asOf, branchId);
    }

    private TipJarDTO.PayoutDTO toPayoutDTO(TipPayout payout) {
        Branch branch = payout.getBranch();
        User createdBy = payout.getCreatedBy();
        return TipJarDTO.PayoutDTO.builder()
                .id(payout.getId())
                .branchId(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .amount(payout.getAmount())
                .payoutDate(payout.getPayoutDate())
                .recipientName(payout.getRecipientName())
                .note(payout.getNote())
                .createdById(createdBy.getId())
                .createdByName(createdBy.getFullName())
                .createdAt(payout.getCreatedAt())
                .build();
    }

    private Branch resolveBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + branchId));
    }

    private User currentUser() {
        Long userId = SecurityUtils.currentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
    }
}

package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.FundWithdrawalRequest;
import com.comfy.caseclose.dto.response.FundWithdrawalBranchDTO;
import com.comfy.caseclose.dto.response.FundWithdrawalPotDTO;
import com.comfy.caseclose.dto.response.FundWithdrawalResponseDTO;
import com.comfy.caseclose.dto.response.FundWithdrawalSourceDTO;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.CashMovement;
import com.comfy.caseclose.entity.FundWithdrawal;
import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.FundWithdrawalRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.FundWithdrawalService;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.FundPeriodType;
import com.comfy.caseclose.utils.enums.FundWithdrawalStatus;
import com.comfy.caseclose.utils.enums.MovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundWithdrawalServiceImpl implements FundWithdrawalService {

    private static final List<CashCloseStatus> EXCLUDED_STATUSES =
            List.of(CashCloseStatus.REJECTED, CashCloseStatus.VOIDED);
    private static final String ALL_BRANCHES_LABEL = "Tất cả chi nhánh";

    private final FundWithdrawalRepository fundWithdrawalRepository;
    private final CashCloseRepository cashCloseRepository;
    private final CashMovementRepository cashMovementRepository;
    private final TipRepository tipRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public FundWithdrawalPotDTO getPotData(Long branchId, LocalDate fromDate, LocalDate toDate, String periodType) {
        return buildPotData(branchId, resolveFrom(fromDate), resolveTo(toDate), resolvePeriodType(periodType));
    }

    @Override
    @Transactional
    public FundWithdrawalPotDTO record(FundWithdrawalRequest request) {
        Branch branch = resolveBranch(request.getBranchId());
        LocalDate from = resolveFrom(request.getFromDate());
        LocalDate to = resolveTo(request.getToDate());
        FundPeriodType periodType = resolvePeriodType(request.getPeriodType());

        long systemWithdraw = request.getSystemWithdrawAmount();
        long actualReceived = request.getActualReceivedAmount() != null
                ? request.getActualReceivedAmount()
                : systemWithdraw;

        long generatedPot = generatedPot(sourceCloses(branch.getId(), from, to));
        long alreadyWithdrawn = fundWithdrawalRepository.findPostedOverlapping(from, to, branch.getId()).stream()
                .mapToLong(FundWithdrawal::getSystemWithdrawAmount)
                .sum();
        long systemPotBefore = generatedPot - alreadyWithdrawn;
        if (systemWithdraw > systemPotBefore) {
            throw new BadRequestException(
                    "System withdraw amount (" + systemWithdraw + ") exceeds the remaining pot (" + systemPotBefore + ")");
        }

        fundWithdrawalRepository.save(
                buildWithdrawal(branch, periodType, from, to, systemPotBefore, systemWithdraw, actualReceived, request.getNote()));

        return buildPotData(branch.getId(), from, to, periodType);
    }

    private FundWithdrawal buildWithdrawal(Branch branch, FundPeriodType periodType, LocalDate from, LocalDate to,
                                           long systemPotBefore, long systemWithdraw, long actualReceived, String note) {
        OffsetDateTime now = OffsetDateTime.now();
        FundWithdrawal withdrawal = new FundWithdrawal();
        withdrawal.setBranch(branch);
        withdrawal.setPeriodType(periodType);
        withdrawal.setPeriodFrom(from);
        withdrawal.setPeriodTo(to);
        withdrawal.setCreatedBy(currentUser());
        withdrawal.setSystemPotBefore(systemPotBefore);
        withdrawal.setSystemWithdrawAmount(systemWithdraw);
        withdrawal.setActualReceivedAmount(actualReceived);
        withdrawal.setNote(InputNormalizer.text(note));
        withdrawal.setStatus(FundWithdrawalStatus.POSTED);
        withdrawal.setUpdatedAt(now);
        return withdrawal;
    }

    // ----- pot assembly ---------------------------------------------------------------------------

    private FundWithdrawalPotDTO buildPotData(Long branchId, LocalDate from, LocalDate to, FundPeriodType periodType) {
        List<CashClose> sourceCloses = sourceCloses(branchId, from, to);
        List<FundWithdrawal> logs = fundWithdrawalRepository.findPostedOverlapping(from, to, branchId);
        List<FundWithdrawalSourceDTO> sources = toSources(sourceCloses);

        long generatedPot = generatedPot(sourceCloses);
        long alreadyWithdrawn = logs.stream().mapToLong(FundWithdrawal::getSystemWithdrawAmount).sum();
        long actualReceived = logs.stream().mapToLong(FundWithdrawal::getActualReceivedAmount).sum();
        long variance = logs.stream().mapToLong(this::variance).sum();

        return FundWithdrawalPotDTO.builder()
                .scope(buildScope(branchId, from, to, periodType))
                .summary(FundWithdrawalPotDTO.Summary.builder()
                        .generatedPot(generatedPot)
                        .alreadyWithdrawn(alreadyWithdrawn)
                        .actualReceived(actualReceived)
                        .variance(variance)
                        .remainingPot(generatedPot - alreadyWithdrawn)
                        .sourceCount(sources.size())
                        .logCount(logs.size())
                        .build())
                .byBranch(buildByBranch(sourceCloses, logs))
                .sources(sources)
                .logs(logs.stream().map(this::toLogDTO).toList())
                .build();
    }

    private List<FundWithdrawalBranchDTO> buildByBranch(List<CashClose> sourceCloses, List<FundWithdrawal> logs) {
        Map<Long, BranchAccumulator> byBranch = new LinkedHashMap<>();
        sourceCloses.forEach(cc -> byBranch
                .computeIfAbsent(cc.getBranch().getId(), id -> new BranchAccumulator(cc.getBranch()))
                .generatedPot += cc.getWithdrawalAmount());
        logs.forEach(log -> {
            BranchAccumulator acc = byBranch.computeIfAbsent(log.getBranch().getId(), id -> new BranchAccumulator(log.getBranch()));
            acc.withdrawn += log.getSystemWithdrawAmount();
            acc.actualReceived += log.getActualReceivedAmount();
            acc.variance += variance(log);
        });
        return byBranch.values().stream()
                .map(BranchAccumulator::toDTO)
                .sorted(Comparator.comparing(FundWithdrawalBranchDTO::getBranchName))
                .toList();
    }

    private List<FundWithdrawalSourceDTO> toSources(List<CashClose> closes) {
        if (closes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = closes.stream().map(CashClose::getId).toList();
        Map<Long, Long> endOfDayByClose = cashMovementRepository.findByCashCloseIdIn(ids).stream()
                .filter(m -> m.getMovementType() == MovementType.END_OF_DAY_EXPENSE)
                .collect(Collectors.groupingBy(m -> m.getCashClose().getId(), Collectors.summingLong(CashMovement::getAmount)));
        Map<Long, Long> tipsByClose = tipRepository.findByCashCloseIdIn(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getCashClose().getId(), Collectors.summingLong(Tip::getAmount)));

        return closes.stream()
                .sorted(Comparator.comparing(CashClose::getBusinessDate).thenComparing(CashClose::getSubmittedAt).reversed())
                .map(cc -> toSourceDTO(cc,
                        endOfDayByClose.getOrDefault(cc.getId(), 0L),
                        tipsByClose.getOrDefault(cc.getId(), 0L)))
                .toList();
    }

    private FundWithdrawalSourceDTO toSourceDTO(CashClose cc, long endOfDayExpense, long tipsAmount) {
        return FundWithdrawalSourceDTO.builder()
                .cashCloseId(cc.getId())
                .referenceCode(cc.getReferenceCode())
                .businessDate(cc.getBusinessDate())
                .branchId(cc.getBranch().getId())
                .branchCode(cc.getBranch().getBranchCode())
                .branchName(cc.getBranch().getBranchName())
                .shiftName(cc.getShiftType().getShiftName())
                .submittedByName(cc.getSubmittedBy().getFullName())
                .withdrawalAmount(cc.getWithdrawalAmount())
                .cashRemaining(cc.getCountedCash() - cc.getWithdrawalAmount() - tipsAmount - endOfDayExpense)
                .note(cc.getNote())
                .build();
    }

    private FundWithdrawalResponseDTO toLogDTO(FundWithdrawal withdrawal) {
        Branch branch = withdrawal.getBranch();
        User createdBy = withdrawal.getCreatedBy();
        return FundWithdrawalResponseDTO.builder()
                .id(withdrawal.getId())
                .branchId(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .periodType(withdrawal.getPeriodType().name())
                .periodFrom(withdrawal.getPeriodFrom())
                .periodTo(withdrawal.getPeriodTo())
                .createdById(createdBy.getId())
                .createdByName(createdBy.getFullName())
                .createdAt(withdrawal.getCreatedAt())
                .systemPotBefore(withdrawal.getSystemPotBefore())
                .systemWithdrawAmount(withdrawal.getSystemWithdrawAmount())
                .actualReceivedAmount(withdrawal.getActualReceivedAmount())
                .varianceAmount(variance(withdrawal))
                .systemPotAfter(withdrawal.getSystemPotBefore() - withdrawal.getActualReceivedAmount())
                .note(withdrawal.getNote())
                .status(withdrawal.getStatus().name())
                .build();
    }

    private FundWithdrawalPotDTO.Scope buildScope(Long branchId, LocalDate from, LocalDate to, FundPeriodType periodType) {
        Branch branch = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
        return FundWithdrawalPotDTO.Scope.builder()
                .branchId(branchId)
                .branchCode(branch == null ? null : branch.getBranchCode())
                .branchLabel(branch == null ? ALL_BRANCHES_LABEL : branch.getBranchName())
                .fromDate(from)
                .toDate(to)
                .periodType(periodType.name())
                .build();
    }

    // ----- helpers --------------------------------------------------------------------------------

    private List<CashClose> sourceCloses(Long branchId, LocalDate from, LocalDate to) {
        return cashCloseRepository.findForReport(from, to, EXCLUDED_STATUSES, branchId).stream()
                .filter(cc -> cc.getWithdrawalAmount() > 0)
                .toList();
    }

    private long generatedPot(List<CashClose> closes) {
        return closes.stream().mapToLong(CashClose::getWithdrawalAmount).sum();
    }

    private long variance(FundWithdrawal withdrawal) {
        return withdrawal.getActualReceivedAmount() - withdrawal.getSystemWithdrawAmount();
    }

    private LocalDate resolveFrom(LocalDate fromDate) {
        return fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate resolveTo(LocalDate toDate) {
        return toDate != null ? toDate : LocalDate.now();
    }

    private FundPeriodType resolvePeriodType(String periodType) {
        return periodType == null || periodType.isBlank() ? FundPeriodType.CUSTOM : FundPeriodType.valueOf(periodType);
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

    private static final class BranchAccumulator {
        private final Branch branch;
        private long generatedPot;
        private long withdrawn;
        private long actualReceived;
        private long variance;

        private BranchAccumulator(Branch branch) {
            this.branch = branch;
        }

        private FundWithdrawalBranchDTO toDTO() {
            return FundWithdrawalBranchDTO.builder()
                    .branchId(branch.getId())
                    .branchCode(branch.getBranchCode())
                    .branchName(branch.getBranchName())
                    .generatedPot(generatedPot)
                    .withdrawn(withdrawn)
                    .actualReceived(actualReceived)
                    .variance(variance)
                    .remainingPot(generatedPot - withdrawn)
                    .build();
        }
    }
}

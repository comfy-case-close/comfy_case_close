package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.response.BranchReportDTO;
import com.comfy.caseclose.dto.response.DateReportDTO;
import com.comfy.caseclose.dto.response.DetailItemDTO;
import com.comfy.caseclose.dto.response.EmployeeReportDTO;
import com.comfy.caseclose.dto.response.IssueReportDTO;
import com.comfy.caseclose.dto.response.KpiReportDTO;
import com.comfy.caseclose.dto.response.ReportScopeDTO;
import com.comfy.caseclose.dto.response.RiskBreakdownDTO;
import com.comfy.caseclose.dto.response.ShiftTypeReportDTO;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.CashDiffExplanation;
import com.comfy.caseclose.entity.CashMovement;
import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashDiffExplanationRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserBranchRepository;
import com.comfy.caseclose.security.CustomUserDetails;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.ReportService;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import com.comfy.caseclose.utils.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final List<CashCloseStatus> EXCLUDED_STATUSES =
            List.of(CashCloseStatus.REJECTED, CashCloseStatus.VOIDED);
    private static final Set<DiffReasonType> OPERATIONAL_REASONS = Set.of(
            DiffReasonType.UNPAID_BILL, DiffReasonType.CUSTOMER_REFUND,
            DiffReasonType.POS_ERROR, DiffReasonType.MISCOUNT, DiffReasonType.OTHER);
    private static final Set<DiffReasonType> OTHER_OPERATIONAL_REASONS = Set.of(
            DiffReasonType.CUSTOMER_REFUND, DiffReasonType.POS_ERROR,
            DiffReasonType.MISCOUNT, DiffReasonType.OTHER);
    private static final String ALL_BRANCHES_LABEL = "Tất cả chi nhánh";

    private final CashCloseRepository cashCloseRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashDiffExplanationRepository cashDiffExplanationRepository;
    private final TipRepository tipRepository;
    private final BranchRepository branchRepository;
    private final UserBranchRepository userBranchRepository;

    @Override
    @Transactional(readOnly = true)
    public KpiReportDTO kpi(Long branchId, LocalDate fromDate, LocalDate toDate) {
        ReportContext context = load(branchId, fromDate, toDate);
        Aggregate overall = new Aggregate();
        context.rows().forEach(overall::add);

        long totalShiftClose = overall.totalShiftClose;
        return KpiReportDTO.builder()
                .scope(context.scope())
                .totalShiftClose(totalShiftClose)
                .totalPosExpectedCash(overall.totalPosExpectedCash)
                .totalCountedCash(overall.totalCountedCash)
                .totalCashDiffNet(overall.totalCashDiffNet)
                .totalCashDiffAbs(overall.totalCashDiffAbs)
                .totalWithdrawal(overall.totalWithdrawal)
                .totalExpense(overall.totalExpense)
                .totalTips(overall.totalTips)
                .totalUnexplainedDiff(overall.totalUnexplainedDiff)
                .totalBillIssueAmount(overall.totalBillIssueAmount)
                .totalOperationalIssueAmount(overall.totalOperationalIssueAmount)
                .totalOtherOperationalIssueAmount(
                        Math.max(0, overall.totalOperationalIssueAmount - overall.totalBillIssueAmount))
                .totalCashIssueAmount(overall.totalCashIssueAmount)
                .warningCount(overall.warningCount)
                .pendingReviewCount(overall.pendingReviewCount)
                .approvedCount(overall.approvedCount)
                .lateCount(overall.lateCount)
                .morningCount(overall.morningCount)
                .eveningCount(overall.eveningCount)
                .issueShiftCount(overall.issueShiftCount)
                .avgWithdrawalPerShift(perShift(overall.totalWithdrawal, totalShiftClose))
                .avgExpensePerShift(perShift(overall.totalExpense, totalShiftClose))
                .avgTipsPerShift(perShift(overall.totalTips, totalShiftClose))
                .avgPosPerShift(perShift(overall.totalPosExpectedCash, totalShiftClose))
                .avgCountedPerShift(perShift(overall.totalCountedCash, totalShiftClose))
                .issueRate(rate(overall.issueShiftCount, totalShiftClose))
                .pendingRate(rate(overall.pendingReviewCount, totalShiftClose))
                .dataDays(context.rows().stream().map(Row::businessDate).distinct().count())
                .branchCount(branchCount(branchId, totalShiftClose, context.accessibleBranchIds()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchReportDTO> byBranch(Long branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(), Row::branchId).values().stream()
                .map(this::toBranchDTO)
                .sorted(Comparator.comparingLong(BranchReportDTO::getTotalWithdrawal).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DateReportDTO> byDate(Long branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(), Row::businessDate).values().stream()
                .map(this::toDateDTO)
                .sorted(Comparator.comparing(DateReportDTO::getBusinessDate).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftTypeReportDTO> byShiftType(Long branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(),
                row -> row.cashClose.getShiftType().getId()).values().stream()
                .map(this::toShiftTypeDTO)
                .sorted(Comparator.comparingLong(ShiftTypeReportDTO::getTotalShiftClose).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeReportDTO> byEmployee(Long branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(),
                row -> row.cashClose.getSubmittedBy().getId()).values().stream()
                .map(this::toEmployeeDTO)
                .sorted(Comparator.comparingLong(EmployeeReportDTO::getPerformanceScore).reversed()
                        .thenComparing(Comparator.comparingLong(EmployeeReportDTO::getTotalShiftClose).reversed()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueReportDTO> issues(Long branchId, LocalDate fromDate, LocalDate toDate, int limit) {
        return load(branchId, fromDate, toDate).rows().stream()
                .filter(Row::issue)
                .limit(limit)
                .map(this::toIssueDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetailItemDTO> details(Long branchId, LocalDate fromDate, LocalDate toDate, String category) {
        List<Row> rows = load(branchId, fromDate, toDate).rows();
        Map<Long, Row> rowByClose = rows.stream()
                .collect(Collectors.toMap(row -> row.cashClose.getId(), row -> row, (a, b) -> a));
        List<Long> ids = rows.stream().map(row -> row.cashClose.getId()).toList();

        List<DetailItemDTO> items = switch (category) {
            case "withdrawal" -> rows.stream()
                    .filter(row -> row.cashClose.getWithdrawalAmount() > 0)
                    .map(row -> detail(row, row.cashClose.getWithdrawalAmount(), "Rút khỏi két", null))
                    .toList();
            case "tips" -> rows.stream()
                    .filter(row -> row.tipsAmount > 0)
                    .map(row -> detail(row, row.tipsAmount, "Tiền tip", null))
                    .toList();
            case "unexplained" -> rows.stream()
                    .filter(row -> row.dayUnexplainedDiff != 0)
                    .map(row -> detail(row, row.dayUnexplainedDiff, "Lệch chưa giải thích", null))
                    .toList();
            case "pos" -> rows.stream()
                    .map(row -> detail(row, row.cashClose.getPosExpectedCash(), "POS kỳ vọng",
                            "Đã đếm: " + row.cashClose.getCountedCash()))
                    .toList();
            case "shifts" -> rows.stream()
                    .map(row -> detail(row, row.cashRemaining, row.cashClose.getStatus().name(), null))
                    .toList();
            case "expense" -> cashMovementRepository.findByCashCloseIdIn(ids).stream()
                    .map(movement -> detail(rowByClose.get(movement.getCashClose().getId()),
                            movement.getAmount(), movement.getCategory().name(), movement.getDescription()))
                    .toList();
            case "unpaid-bill" -> explanationDetails(ids, rowByClose, DiffReasonType.UNPAID_BILL::equals);
            case "other-ops" -> explanationDetails(ids, rowByClose, OTHER_OPERATIONAL_REASONS::contains);
            default -> throw new BadRequestException("Unknown report category: " + category);
        };

        return items.stream()
                .sorted(Comparator.comparing(DetailItemDTO::getBusinessDate)
                        .thenComparing(DetailItemDTO::getSubmittedAt)
                        .reversed())
                .toList();
    }

    private List<DetailItemDTO> explanationDetails(List<Long> ids, Map<Long, Row> rowByClose, Predicate<DiffReasonType> matcher) {
        return cashDiffExplanationRepository.findByCashCloseIdIn(ids).stream()
                .filter(explanation -> matcher.test(explanation.getReasonType()))
                .map(explanation -> detail(rowByClose.get(explanation.getCashClose().getId()),
                        Math.abs(explanation.getSignedAmount()), explanation.getReasonType().name(), explanation.getNote()))
                .toList();
    }

    private DetailItemDTO detail(Row row, long amount, String label, String note) {
        CashClose cc = row.cashClose;
        return DetailItemDTO.builder()
                .cashCloseId(cc.getId())
                .referenceCode(cc.getReferenceCode())
                .businessDate(cc.getBusinessDate())
                .branchId(cc.getBranch().getId())
                .branchCode(cc.getBranch().getBranchCode())
                .branchName(cc.getBranch().getBranchName())
                .shiftTypeCode(cc.getShiftType().getShiftTypeCode())
                .shiftName(cc.getShiftType().getShiftName())
                .submittedByName(cc.getSubmittedBy().getFullName())
                .submittedAt(cc.getSubmittedAt())
                .amount(amount)
                .label(label)
                .note(note)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public KpiReportDTO monthlyExport(Long branchId, YearMonth month) {
        return kpi(branchId, month.atDay(1), month.atEndOfMonth());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskBreakdownDTO> riskBreakdown(Long branchId, LocalDate fromDate, LocalDate toDate) {
        Map<RiskLevel, Long> counts = load(branchId, fromDate, toDate).rows().stream()
                .collect(Collectors.groupingBy(Row::riskLevel, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> RiskBreakdownDTO.builder()
                        .riskLevel(entry.getKey().name())
                        .count(entry.getValue())
                        .build())
                .sorted(Comparator.comparingLong(RiskBreakdownDTO::getCount).reversed())
                .toList();
    }

    // ----- row loading ----------------------------------------------------------------------------

    private ReportContext load(Long branchId, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        List<Long> accessibleBranchIds = accessibleBranchIds();

        List<CashClose> closes = cashCloseRepository
                .findForReport(range.from(), range.to(), EXCLUDED_STATUSES, branchId).stream()
                .filter(cc -> accessibleBranchIds == null || accessibleBranchIds.contains(cc.getBranch().getId()))
                .toList();

        List<Row> rows = buildRows(closes);
        return new ReportContext(rows, buildScope(branchId, range), accessibleBranchIds);
    }

    private List<Row> buildRows(List<CashClose> closes) {
        if (closes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = closes.stream().map(CashClose::getId).toList();
        Map<Long, List<CashMovement>> movementsByClose = cashMovementRepository.findByCashCloseIdIn(ids).stream()
                .collect(Collectors.groupingBy(m -> m.getCashClose().getId()));
        Map<Long, List<CashDiffExplanation>> explanationsByClose = cashDiffExplanationRepository.findByCashCloseIdIn(ids).stream()
                .collect(Collectors.groupingBy(e -> e.getCashClose().getId()));
        Map<Long, Long> tipsByClose = tipRepository.findByCashCloseIdIn(ids).stream()
                .collect(Collectors.groupingBy(t -> t.getCashClose().getId(), Collectors.summingLong(Tip::getAmount)));

        List<Row> rows = closes.stream()
                .map(cc -> toRow(cc,
                        movementsByClose.getOrDefault(cc.getId(), List.of()),
                        explanationsByClose.getOrDefault(cc.getId(), List.of()),
                        tipsByClose.getOrDefault(cc.getId(), 0L)))
                .collect(Collectors.toList());

        assignDayRepresentativeUnexplained(rows);
        rows.sort(Comparator.comparing(Row::businessDate)
                .thenComparing(Row::submittedAt)
                .reversed());
        return rows;
    }

    private Row toRow(CashClose cc, List<CashMovement> movements, List<CashDiffExplanation> explanations, long tipsAmount) {
        long expense = sum(movements, MovementType.EXPENSE);
        long endOfDayExpense = sum(movements, MovementType.END_OF_DAY_EXPENSE);
        long totalExpense = expense + endOfDayExpense;
        long explainedDiff = explanations.stream().mapToLong(CashDiffExplanation::getSignedAmount).sum();
        long billIssue = issueAmount(explanations, DiffReasonType.UNPAID_BILL::equals);
        long operationalIssue = issueAmount(explanations, OPERATIONAL_REASONS::contains);
        long cashDiff = cc.getPosExpectedCash() - cc.getCountedCash();
        long cashRemaining = cc.getCountedCash() - cc.getWithdrawalAmount() - tipsAmount - endOfDayExpense;
        String shiftCode = cc.getShiftType().getShiftTypeCode();

        Row row = new Row();
        row.cashClose = cc;
        row.totalExpense = totalExpense;
        row.tipsAmount = tipsAmount;
        row.cashDiff = cashDiff;
        row.explainedDiff = explainedDiff;
        row.billIssueAmount = billIssue;
        row.operationalIssueAmount = operationalIssue;
        row.cashRemaining = cashRemaining;
        row.warning = cc.getRiskLevel() == RiskLevel.HIGH || cc.getRiskLevel() == RiskLevel.CRITICAL;
        row.pending = cc.getStatus() == CashCloseStatus.PENDING_REVIEW;
        row.approved = cc.getStatus() == CashCloseStatus.APPROVED;
        row.late = Boolean.TRUE.equals(cc.getIsLate());
        row.morning = "MORNING_CLOSE".equals(shiftCode);
        row.evening = "EVENING_CLOSE".equals(shiftCode);
        return row;
    }

    // The day's unexplained diff is carried only by the day's closing shift, so morning + evening of the
    // same day are never double-counted (mirrors getDashboardData in Code.gs).
    private void assignDayRepresentativeUnexplained(List<Row> rows) {
        Map<String, List<Row>> byDay = rows.stream()
                .collect(Collectors.groupingBy(row -> row.branchId() + "|" + row.businessDate()));

        for (List<Row> dayRows : byDay.values()) {
            long dayUnexplained = dayRows.stream().mapToLong(r -> r.cashDiff - r.explainedDiff).sum();
            Row representative = dayRows.stream()
                    .max(Comparator.comparing((Row r) -> r.cashClose.getShiftType().getSortOrder())
                            .thenComparing(Row::submittedAt))
                    .orElseThrow();
            representative.dayUnexplainedDiff = dayUnexplained;
            dayRows.forEach(row -> row.totalCashIssueAmount =
                    row.operationalIssueAmount + Math.abs(row.dayUnexplainedDiff));
        }
    }

    // ----- aggregation ----------------------------------------------------------------------------

    private <K> Map<K, Aggregate> groupAggregates(List<Row> rows, Function<Row, K> keyExtractor) {
        Map<K, Aggregate> aggregates = new TreeMap<>();
        for (Row row : rows) {
            aggregates.computeIfAbsent(keyExtractor.apply(row), key -> new Aggregate()).add(row);
        }
        return aggregates;
    }

    private BranchReportDTO toBranchDTO(Aggregate agg) {
        Row latest = agg.latest;
        return BranchReportDTO.builder()
                .branchId(latest.branchId())
                .branchCode(latest.cashClose.getBranch().getBranchCode())
                .branchName(latest.cashClose.getBranch().getBranchName())
                .totalShiftClose(agg.totalShiftClose)
                .totalWithdrawal(agg.totalWithdrawal)
                .totalExpense(agg.totalExpense)
                .totalUnexplainedDiff(agg.totalUnexplainedDiff)
                .warningCount(agg.warningCount)
                .pendingReviewCount(agg.pendingReviewCount)
                .issueRate(rate(agg.issueShiftCount, agg.totalShiftClose))
                .pendingRate(rate(agg.pendingReviewCount, agg.totalShiftClose))
                .latestCashCloseId(latest.cashClose.getId())
                .latestReferenceCode(latest.cashClose.getReferenceCode())
                .latestBusinessDate(latest.businessDate())
                .latestShiftName(latest.cashClose.getShiftType().getShiftName())
                .latestCashRemaining(latest.cashRemaining)
                .build();
    }

    private DateReportDTO toDateDTO(Aggregate agg) {
        return DateReportDTO.builder()
                .businessDate(agg.latest.businessDate())
                .totalShiftClose(agg.totalShiftClose)
                .totalWithdrawal(agg.totalWithdrawal)
                .totalExpense(agg.totalExpense)
                .totalUnexplainedDiff(agg.totalUnexplainedDiff)
                .warningCount(agg.warningCount)
                .pendingReviewCount(agg.pendingReviewCount)
                .build();
    }

    private ShiftTypeReportDTO toShiftTypeDTO(Aggregate agg) {
        var shiftType = agg.latest.cashClose.getShiftType();
        return ShiftTypeReportDTO.builder()
                .shiftTypeId(shiftType.getId())
                .shiftTypeCode(shiftType.getShiftTypeCode())
                .shiftName(shiftType.getShiftName())
                .totalShiftClose(agg.totalShiftClose)
                .totalWithdrawal(agg.totalWithdrawal)
                .totalExpense(agg.totalExpense)
                .warningCount(agg.warningCount)
                .pendingReviewCount(agg.pendingReviewCount)
                .issueRate(rate(agg.issueShiftCount, agg.totalShiftClose))
                .pendingRate(rate(agg.pendingReviewCount, agg.totalShiftClose))
                .build();
    }

    private EmployeeReportDTO toEmployeeDTO(Aggregate agg) {
        var employee = agg.latest.cashClose.getSubmittedBy();
        long score = performanceScore(agg);
        return EmployeeReportDTO.builder()
                .submittedById(employee.getId())
                .submittedByCode(employee.getEmployeeCode())
                .submittedByName(employee.getFullName())
                .totalShiftClose(agg.totalShiftClose)
                .totalWithdrawal(agg.totalWithdrawal)
                .warningCount(agg.warningCount)
                .pendingReviewCount(agg.pendingReviewCount)
                .totalUnexplainedDiff(agg.totalUnexplainedDiff)
                .totalBillIssueAmount(agg.totalBillIssueAmount)
                .totalCashIssueAmount(agg.totalCashIssueAmount)
                .performanceScore(score)
                .performanceLabel(performanceLabel(score))
                .build();
    }

    private IssueReportDTO toIssueDTO(Row row) {
        CashClose cc = row.cashClose;
        return IssueReportDTO.builder()
                .id(cc.getId())
                .referenceCode(cc.getReferenceCode())
                .businessDate(cc.getBusinessDate())
                .branchId(cc.getBranch().getId())
                .branchCode(cc.getBranch().getBranchCode())
                .branchName(cc.getBranch().getBranchName())
                .shiftTypeCode(cc.getShiftType().getShiftTypeCode())
                .shiftName(cc.getShiftType().getShiftName())
                .submittedByName(cc.getSubmittedBy().getFullName())
                .submittedAt(cc.getSubmittedAt())
                .billIssueAmount(row.billIssueAmount)
                .unexplainedDiff(row.dayUnexplainedDiff)
                .totalCashIssueAmount(row.totalCashIssueAmount)
                .totalExpense(row.totalExpense)
                .withdrawalAmount(cc.getWithdrawalAmount())
                .status(cc.getStatus().name())
                .riskLevel(cc.getRiskLevel().name())
                .build();
    }

    // Performance score mirrors performanceScoreForAgg in Code.gs (rounded, not floored).
    private long performanceScore(Aggregate agg) {
        long score = 100
                + agg.totalShiftClose * 4
                - agg.warningCount * 8
                - agg.pendingReviewCount * 12
                - Math.round(agg.totalUnexplainedDiff / 50_000.0) * 4
                - Math.round(agg.totalBillIssueAmount / 50_000.0) * 5
                - Math.round(agg.totalCashIssueAmount / 100_000.0) * 3;
        return Math.max(0, Math.min(100, score));
    }

    private String performanceLabel(long score) {
        if (score >= 90) {
            return "Rất tốt";
        }
        if (score >= 75) {
            return "Ổn định";
        }
        if (score >= 60) {
            return "Cần theo dõi";
        }
        return "Cần cải thiện";
    }

    // ----- helpers --------------------------------------------------------------------------------

    private ReportScopeDTO buildScope(Long branchId, DateRange range) {
        String label = branchId == null
                ? ALL_BRANCHES_LABEL
                : branchRepository.findById(branchId).map(b -> b.getBranchName()).orElse(ALL_BRANCHES_LABEL);
        return ReportScopeDTO.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .branchId(branchId)
                .branchLabel(label)
                .totalDays(ChronoUnit.DAYS.between(range.from(), range.to()) + 1)
                .build();
    }

    private long branchCount(Long branchId, long totalShiftClose, List<Long> accessibleBranchIds) {
        if (branchId != null) {
            return totalShiftClose > 0 ? 1 : 0;
        }
        List<Long> activeIds = branchRepository.findActiveBranchIds();
        if (accessibleBranchIds == null) {
            return activeIds.size();
        }
        return activeIds.stream().filter(accessibleBranchIds::contains).count();
    }

    private List<Long> accessibleBranchIds() {
        CustomUserDetails user = SecurityUtils.currentUser();
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.ACCOUNTANT) {
            return null;
        }
        return userBranchRepository.findBranchIdsByUserId(user.getId());
    }

    private long sum(List<CashMovement> movements, MovementType type) {
        return movements.stream()
                .filter(movement -> movement.getMovementType() == type)
                .mapToLong(CashMovement::getAmount)
                .sum();
    }

    private long issueAmount(List<CashDiffExplanation> explanations, Predicate<DiffReasonType> matcher) {
        return explanations.stream()
                .filter(e -> matcher.test(e.getReasonType()))
                .mapToLong(e -> Math.abs(e.getSignedAmount()))
                .sum();
    }

    private long perShift(long total, long shiftCount) {
        return shiftCount == 0 ? 0 : Math.round((double) total / shiftCount);
    }

    private double rate(long count, long shiftCount) {
        return shiftCount == 0 ? 0 : Math.round(((double) count / shiftCount) * 1000) / 10.0;
    }

    // ----- internal types -------------------------------------------------------------------------

    private static final class Row {
        private CashClose cashClose;
        private long totalExpense;
        private long tipsAmount;
        private long cashDiff;
        private long explainedDiff;
        private long billIssueAmount;
        private long operationalIssueAmount;
        private long dayUnexplainedDiff;
        private long totalCashIssueAmount;
        private long cashRemaining;
        private boolean warning;
        private boolean pending;
        private boolean approved;
        private boolean late;
        private boolean morning;
        private boolean evening;

        private Long branchId() {
            return cashClose.getBranch().getId();
        }

        private LocalDate businessDate() {
            return cashClose.getBusinessDate();
        }

        private OffsetDateTime submittedAt() {
            return cashClose.getSubmittedAt();
        }

        private RiskLevel riskLevel() {
            return cashClose.getRiskLevel();
        }

        private boolean issue() {
            return totalCashIssueAmount > 0 || warning || pending;
        }
    }

    private static final class Aggregate {
        private long totalShiftClose;
        private long totalPosExpectedCash;
        private long totalCountedCash;
        private long totalCashDiffNet;
        private long totalCashDiffAbs;
        private long totalWithdrawal;
        private long totalExpense;
        private long totalTips;
        private long totalUnexplainedDiff;
        private long totalBillIssueAmount;
        private long totalOperationalIssueAmount;
        private long totalCashIssueAmount;
        private long warningCount;
        private long pendingReviewCount;
        private long approvedCount;
        private long lateCount;
        private long morningCount;
        private long eveningCount;
        private long issueShiftCount;
        private Row latest;

        private void add(Row row) {
            totalShiftClose++;
            totalPosExpectedCash += row.cashClose.getPosExpectedCash();
            totalCountedCash += row.cashClose.getCountedCash();
            totalCashDiffNet += row.cashDiff;
            totalCashDiffAbs += Math.abs(row.cashDiff);
            totalWithdrawal += row.cashClose.getWithdrawalAmount();
            totalExpense += row.totalExpense;
            totalTips += row.tipsAmount;
            totalUnexplainedDiff += Math.abs(row.dayUnexplainedDiff);
            totalBillIssueAmount += row.billIssueAmount;
            totalOperationalIssueAmount += row.operationalIssueAmount;
            totalCashIssueAmount += row.totalCashIssueAmount;
            warningCount += row.warning ? 1 : 0;
            pendingReviewCount += row.pending ? 1 : 0;
            approvedCount += row.approved ? 1 : 0;
            lateCount += row.late ? 1 : 0;
            morningCount += row.morning ? 1 : 0;
            eveningCount += row.evening ? 1 : 0;
            issueShiftCount += row.issue() ? 1 : 0;
            if (latest == null) {
                latest = row;
            }
        }
    }

    private record ReportContext(List<Row> rows, ReportScopeDTO scope, List<Long> accessibleBranchIds) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
        private static DateRange resolve(LocalDate fromDate, LocalDate toDate) {
            LocalDate today = LocalDate.now();
            LocalDate from = fromDate != null ? fromDate : today.withDayOfMonth(1);
            LocalDate to = toDate != null ? toDate : today;
            return from.isAfter(to) ? new DateRange(to, from) : new DateRange(from, to);
        }
    }
}

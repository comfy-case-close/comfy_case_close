package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.response.BranchReportDTO;
import com.fnbx.cashclose.dto.response.DateReportDTO;
import com.fnbx.cashclose.dto.response.DetailItemDTO;
import com.fnbx.cashclose.dto.response.EmployeeReportDTO;
import com.fnbx.cashclose.dto.response.IssueReportDTO;
import com.fnbx.cashclose.dto.response.KpiReportDTO;
import com.fnbx.cashclose.dto.response.ReportScopeDTO;
import com.fnbx.cashclose.dto.response.RiskBreakdownDTO;
import com.fnbx.cashclose.dto.response.ShiftTypeReportDTO;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.enums.RiskLevel;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.MovementKindRepository;
import com.fnbx.cashclose.service.CashCloseView;
import com.fnbx.cashclose.service.ReportService;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.identity.entity.ShiftType;
import com.fnbx.identity.entity.Staff;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dashboard reports, ported from {@code dev} onto the split-service DDL.
 *
 * <h2>What changed against dev, and why</h2>
 * <ul>
 *   <li><b>Figures come from {@code cashclose.v_close_calc}</b> ({@link CashCloseCalc}):
 *       counted cash, difference, unexplained, expense total and cash remaining are
 *       read, never re-added in Java. {@code unexplained = cashDifference - explained
 *       - pending} is the view's formula.</li>
 *   <li><b>One ledger instead of three tables.</b> dev's {@code CashMovement},
 *       {@code CashDiffExplanation} and {@code Tip} are all rows of
 *       {@code cashclose.cash_movement}; the kind ({@code platform.movement_kind})
 *       says what a line is. Tips are {@code TIPS} lines, bill / operational issues
 *       are matched on dev's reason names as kind codes.</li>
 *   <li><b>REJECTED lines count as never declared</b>, exactly as in the view, so
 *       every line-level figure here agrees with the close's own totals.</li>
 *   <li><b>Sign convention follows the DDL:</b> {@code cashDifference = counted - POS},
 *       negative = SHORT. dev's {@code pos - counted} is inverted.</li>
 *   <li><b>Risk is derived</b> ({@link CashCloseView}) from the unexplained gap and
 *       the thresholds snapshotted at submit: LOW / MEDIUM / HIGH. There is no
 *       CRITICAL, so a "warning" is HIGH.</li>
 *   <li><b>Access</b> is {@code REPORT_READ} per branch through
 *       {@link BranchAccessGuard}, not dev's ADMIN/ACCOUNTANT role shortcut.
 *       "Today" is the business's own timezone, not a hardcoded zone.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final List<CloseStatus> EXCLUDED_STATUSES =
            List.of(CloseStatus.REJECTED, CloseStatus.VOIDED);

    /** The tips ledger kind - see migration 007-cashclose-006-shift-tips. */
    private static final String TIPS_KIND = "TIPS";
    private static final String UNPAID_BILL_KIND = "UNPAID_BILL";
    /**
     * dev's operational {@code DiffReasonType}s, matched against
     * {@code movement_kind.kind_code}. The seed catalogue has UNPAID_BILL, POS_ERROR
     * and MISCOUNT; CUSTOMER_REFUND and OTHER only match if a business defines them.
     */
    private static final Set<String> OPERATIONAL_KINDS = Set.of(
            UNPAID_BILL_KIND, "CUSTOMER_REFUND", "POS_ERROR", "MISCOUNT", "OTHER");
    private static final Set<String> OTHER_OPERATIONAL_KINDS = Set.of(
            "CUSTOMER_REFUND", "POS_ERROR", "MISCOUNT", "OTHER");
    /** dev's shift codes. Shift types are per business now - these must match its codes. */
    private static final String MORNING_SHIFT_CODE = "MORNING_CLOSE";
    private static final String EVENING_SHIFT_CODE = "EVENING_CLOSE";
    private static final String ALL_BRANCHES_LABEL = "Tất cả chi nhánh";

    private final CashCloseRepository cashCloseRepository;
    private final CashCloseCalcRepository cashCloseCalcRepository;
    private final CashMovementRepository cashMovementRepository;
    private final MovementKindRepository movementKindRepository;
    private final BranchAccessGuard branchAccess;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public KpiReportDTO kpi(UUID branchId, LocalDate fromDate, LocalDate toDate) {
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
                .totalTipsSeparate(overall.totalTips - overall.totalTipsInsideDrawer)
                .totalTipsInsideDrawer(overall.totalTipsInsideDrawer)
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
                .avgTipsSeparatePerShift(perShift(overall.totalTips - overall.totalTipsInsideDrawer, totalShiftClose))
                .avgTipsInsideDrawerPerShift(perShift(overall.totalTipsInsideDrawer, totalShiftClose))
                .avgPosPerShift(perShift(overall.totalPosExpectedCash, totalShiftClose))
                .avgCountedPerShift(perShift(overall.totalCountedCash, totalShiftClose))
                .issueRate(rate(overall.issueShiftCount, totalShiftClose))
                .pendingRate(rate(overall.pendingReviewCount, totalShiftClose))
                .dataDays(context.rows().stream().map(Row::businessDate).distinct().count())
                .branchCount(branchCount(branchId, totalShiftClose, context.reportableBranchIds()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchReportDTO> byBranch(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(), Row::branchId).values().stream()
                .map(this::toBranchDTO)
                .sorted(Comparator.comparingLong(BranchReportDTO::getTotalWithdrawal).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DateReportDTO> byDate(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(), Row::businessDate).values().stream()
                .map(this::toDateDTO)
                .sorted(Comparator.comparing(DateReportDTO::getBusinessDate).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftTypeReportDTO> byShiftType(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(),
                row -> row.cashClose.getShiftTypeId()).values().stream()
                .map(this::toShiftTypeDTO)
                .sorted(Comparator.comparingLong(ShiftTypeReportDTO::getTotalShiftClose).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeReportDTO> byEmployee(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        return groupAggregates(load(branchId, fromDate, toDate).rows(),
                row -> row.cashClose.getSubmittedBy()).values().stream()
                .map(this::toEmployeeDTO)
                .sorted(Comparator.comparingLong(EmployeeReportDTO::getPerformanceScore).reversed()
                        .thenComparing(Comparator.comparingLong(EmployeeReportDTO::getTotalShiftClose).reversed()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueReportDTO> issues(UUID branchId, LocalDate fromDate, LocalDate toDate, int limit) {
        return load(branchId, fromDate, toDate).rows().stream()
                .filter(Row::issue)
                .limit(Math.max(0, limit))
                .map(this::toIssueDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetailItemDTO> details(UUID branchId, LocalDate fromDate, LocalDate toDate, String category) {
        List<Row> rows = load(branchId, fromDate, toDate).rows();

        List<DetailItemDTO> items = switch (category) {
            case "withdrawal" -> rows.stream()
                    .filter(row -> row.withdrawal > 0)
                    .map(row -> detail(row, row.withdrawal, "Rút khỏi két", null))
                    .toList();
            case "tips" -> Stream.concat(tipsSeparateDetails(rows), tipsInsideDrawerDetails(rows)).toList();
            case "tips-separate" -> tipsSeparateDetails(rows).toList();
            case "tips-in-drawer" -> tipsInsideDrawerDetails(rows).toList();
            case "unexplained" -> rows.stream()
                    .filter(row -> row.dayUnexplainedDiff != 0)
                    .map(row -> detail(row, row.dayUnexplainedDiff, "Lệch chưa giải thích", null))
                    .toList();
            case "pos" -> rows.stream()
                    .map(row -> detail(row, row.posExpectedCash, "POS kỳ vọng",
                            "Đã đếm: " + row.countedCash))
                    .toList();
            case "shifts" -> rows.stream()
                    .map(row -> detail(row, row.cashRemaining, row.cashClose.getStatus().name(), null))
                    .toList();
            // Real cost only: filtered by expense_category, like v_close_calc.expense_total.
            case "expense" -> lineDetails(rows, line -> line.kind().getExpenseCategory() != null,
                    line -> line.kind().getExpenseCategory());
            case "unpaid-bill" -> lineDetails(rows, line -> UNPAID_BILL_KIND.equals(line.kindCode()),
                    Line::kindCode);
            case "other-ops" -> lineDetails(rows, line -> OTHER_OPERATIONAL_KINDS.contains(line.kindCode()),
                    Line::kindCode);
            default -> throw CashCloseExceptions.invalidFilter("Unknown report category: " + category);
        };

        return items.stream()
                .sorted(Comparator.comparing(DetailItemDTO::getBusinessDate)
                        .thenComparing(DetailItemDTO::getSubmittedAt)
                        .reversed())
                .toList();
    }

    private Stream<DetailItemDTO> tipsSeparateDetails(List<Row> rows) {
        return rows.stream()
                .filter(row -> row.tipsSeparate() > 0)
                .map(row -> detail(row, row.tipsSeparate(), "Tips tách két", null));
    }

    private Stream<DetailItemDTO> tipsInsideDrawerDetails(List<Row> rows) {
        return rows.stream()
                .filter(row -> row.tipsInsideDrawer > 0)
                .map(row -> detail(row, row.tipsInsideDrawer, "Tips nhập két", null));
    }

    /** One detail item per ledger line (dev read explanations / movements again; the rows already hold them). */
    private List<DetailItemDTO> lineDetails(List<Row> rows, Predicate<Line> matcher, Function<Line, String> label) {
        return rows.stream()
                .flatMap(row -> row.lines.stream()
                        .filter(matcher)
                        .map(line -> detail(row, line.absAmount(), label.apply(line), line.movement().getDescription())))
                .toList();
    }

    private DetailItemDTO detail(Row row, long amount, String label, String note) {
        CashClose cc = row.cashClose;
        return DetailItemDTO.builder()
                .cashCloseId(cc.getCashCloseId())
                .referenceCode(cc.getCashCloseCode())
                .businessDate(cc.getBusinessDate())
                .branchId(cc.getBranchId())
                .branchCode(row.branch.getBranchCode())
                .branchName(row.branch.getBranchName())
                .shiftTypeCode(row.shiftType.getShiftCode())
                .shiftName(row.shiftType.getShiftName())
                .submittedByName(fullName(row.submitter))
                .submittedAt(row.submittedAt())
                .amount(amount)
                .label(label)
                .note(note)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public KpiReportDTO monthlyExport(UUID branchId, YearMonth month) {
        // The current month is still running: report it up to today rather than to a future month-end.
        LocalDate today = LocalDate.now(businessZone());
        requireNotInFuture(month.atDay(1), "month", today);
        LocalDate end = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        return kpi(branchId, month.atDay(1), end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskBreakdownDTO> riskBreakdown(UUID branchId, LocalDate fromDate, LocalDate toDate) {
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

    private ReportContext load(UUID branchId, LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = businessZone();
        LocalDate today = LocalDate.now(zone);
        // Every dashboard/report filter funnels through here: no future reporting periods.
        requireNotInFuture(fromDate, "fromDate", today);
        requireNotInFuture(toDate, "toDate", today);
        DateRange range = DateRange.resolve(fromDate, toDate, today);
        Set<UUID> reportableBranchIds = reportableBranchIds(branchId);

        List<CashClose> closes = cashCloseRepository
                .findForReport(range.from(), range.to(), EXCLUDED_STATUSES, reportableBranchIds);

        List<Row> rows = buildRows(closes, zone);
        return new ReportContext(rows, buildScope(branchId, range), reportableBranchIds);
    }

    /**
     * Branches the caller may report on: every branch with {@code REPORT_READ}, or
     * just {@code branchId} when given. Naming a branch outside that set is refused
     * rather than silently returning an empty report.
     */
    private Set<UUID> reportableBranchIds(UUID branchId) {
        Set<UUID> allowed = branchAccess.branches(Permission.REPORT_READ);
        if (allowed.isEmpty()) throw new AccessDeniedException("Reporting access denied");
        if (branchId == null) return allowed;
        if (!allowed.contains(branchId)) throw new AccessDeniedException("Reporting access denied for this branch");
        return Set.of(branchId);
    }

    private List<Row> buildRows(List<CashClose> closes, ZoneId zone) {
        if (closes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = closes.stream().map(CashClose::getCashCloseId).toList();
        Map<UUID, CashCloseCalc> calcByClose = cashCloseCalcRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CashCloseCalc::getCashCloseId, Function.identity()));
        // A REJECTED line counts as never declared - the same rule v_close_calc applies.
        List<CashMovement> movements = cashMovementRepository.findByCashCloseIdIn(ids).stream()
                .filter(m -> m.getApprovalStatus() != MovementStatus.REJECTED)
                .toList();
        Map<Long, MovementKind> kinds = movementKindRepository.findAllById(
                        movements.stream().map(CashMovement::getKindSk).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MovementKind::getKindSk, Function.identity()));
        Map<UUID, List<Line>> linesByClose = movements.stream()
                .map(m -> new Line(m, Objects.requireNonNull(kinds.get(m.getKindSk()), "movement kind " + m.getKindSk())))
                .collect(Collectors.groupingBy(line -> line.movement().getCashCloseId()));

        Map<UUID, Branch> branches = byId(Branch.class, "branchId", Branch::getBranchId,
                closes.stream().map(CashClose::getBranchId).collect(Collectors.toSet()));
        Map<UUID, ShiftType> shiftTypes = byId(ShiftType.class, "shiftTypeId", ShiftType::getShiftTypeId,
                closes.stream().map(CashClose::getShiftTypeId).collect(Collectors.toSet()));
        Map<UUID, Staff> submitters = byId(Staff.class, "staffId", Staff::getStaffId,
                closes.stream().map(CashClose::getSubmittedBy).collect(Collectors.toSet()));

        List<Row> rows = closes.stream()
                .map(cc -> toRow(cc,
                        Objects.requireNonNull(calcByClose.get(cc.getCashCloseId()), "v_close_calc row for " + cc.getCashCloseCode()),
                        linesByClose.getOrDefault(cc.getCashCloseId(), List.of()),
                        branches.get(cc.getBranchId()),
                        shiftTypes.get(cc.getShiftTypeId()),
                        submitters.get(cc.getSubmittedBy()),
                        zone))
                .collect(Collectors.toList());

        assignDayRepresentativeUnexplained(rows);
        rows.sort(Comparator.comparing(Row::businessDate)
                .thenComparing(Row::submittedInstant)
                .reversed());
        return rows;
    }

    private Row toRow(CashClose cc, CashCloseCalc calc, List<Line> lines,
                      Branch branch, ShiftType shiftType, Staff submitter, ZoneId zone) {
        String shiftCode = shiftType.getShiftCode();
        RiskLevel risk = CashCloseView.of(cc, calc, null, null).riskLevel();

        Row row = new Row();
        row.cashClose = cc;
        row.branch = branch;
        row.shiftType = shiftType;
        row.submitter = submitter;
        row.zone = zone;
        row.lines = lines;
        row.posExpectedCash = money(cc.getPosExpectedCash());
        row.withdrawal = money(cc.getWithdrawalAmount());
        row.countedCash = money(calc.getCountedCash());
        // counted - POS: negative = SHORT (DDL sign convention).
        row.cashDiff = money(calc.getCashDifference());
        // cashDifference - explained - pending, straight from the view.
        row.unexplainedDiff = money(calc.getUnexplainedDifference());
        row.totalExpense = money(calc.getExpenseTotal());
        row.cashRemaining = money(calc.getCashRemaining());
        row.tipsAmount = sumAbs(lines, line -> TIPS_KIND.equals(line.kindCode()));
        // "In the drawer" = the tip left the drawer before hand-over, i.e. it was
        // counted with the cash first (v_close_calc.tips_in_drawer_total uses the same flag).
        row.tipsInsideDrawer = sumAbs(lines, line -> TIPS_KIND.equals(line.kindCode()) && line.kind().isAffectsRemaining());
        row.billIssueAmount = sumAbs(lines, line -> UNPAID_BILL_KIND.equals(line.kindCode()));
        row.operationalIssueAmount = sumAbs(lines, line -> OPERATIONAL_KINDS.contains(line.kindCode()));
        // Thresholds are always snapshotted at submit (ck_close_thresholds_snapshotted), so risk is never null.
        row.riskLevel = risk == null ? RiskLevel.LOW : risk;
        row.warning = row.riskLevel == RiskLevel.HIGH;
        row.pending = cc.getStatus() == CloseStatus.PENDING_REVIEW;
        row.approved = cc.getStatus() == CloseStatus.APPROVED;
        row.late = cc.isLate();
        row.morning = MORNING_SHIFT_CODE.equals(shiftCode);
        row.evening = EVENING_SHIFT_CODE.equals(shiftCode);
        return row;
    }

    // The day's unexplained diff is carried only by the day's closing shift, so morning + evening of the
    // same day are never double-counted (mirrors getDashboardData in Code.gs).
    private void assignDayRepresentativeUnexplained(List<Row> rows) {
        Map<String, List<Row>> byDay = rows.stream()
                .collect(Collectors.groupingBy(row -> row.branchId() + "|" + row.businessDate()));

        for (List<Row> dayRows : byDay.values()) {
            long dayUnexplained = dayRows.stream().mapToLong(r -> r.unexplainedDiff).sum();
            Row representative = dayRows.stream()
                    .max(Comparator.comparingInt((Row r) -> r.shiftType.getSortOrder())
                            .thenComparing(Row::submittedInstant))
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
                .branchCode(latest.branch.getBranchCode())
                .branchName(latest.branch.getBranchName())
                .totalShiftClose(agg.totalShiftClose)
                .totalWithdrawal(agg.totalWithdrawal)
                .totalExpense(agg.totalExpense)
                .totalUnexplainedDiff(agg.totalUnexplainedDiff)
                .warningCount(agg.warningCount)
                .pendingReviewCount(agg.pendingReviewCount)
                .issueRate(rate(agg.issueShiftCount, agg.totalShiftClose))
                .pendingRate(rate(agg.pendingReviewCount, agg.totalShiftClose))
                .latestCashCloseId(latest.cashClose.getCashCloseId())
                .latestReferenceCode(latest.cashClose.getCashCloseCode())
                .latestBusinessDate(latest.businessDate())
                .latestShiftName(latest.shiftType.getShiftName())
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
                .issueRate(rate(agg.issueShiftCount, agg.totalShiftClose))
                .pendingRate(rate(agg.pendingReviewCount, agg.totalShiftClose))
                .build();
    }

    private ShiftTypeReportDTO toShiftTypeDTO(Aggregate agg) {
        ShiftType shiftType = agg.latest.shiftType;
        return ShiftTypeReportDTO.builder()
                .shiftTypeId(shiftType.getShiftTypeId())
                .shiftTypeCode(shiftType.getShiftCode())
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
        Staff employee = agg.latest.submitter;
        long score = performanceScore(agg);
        return EmployeeReportDTO.builder()
                .submittedById(agg.latest.cashClose.getSubmittedBy())
                .submittedByCode(employee == null ? null : employee.getEmployeeCode())
                .submittedByName(fullName(employee))
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
                .id(cc.getCashCloseId())
                .referenceCode(cc.getCashCloseCode())
                .businessDate(cc.getBusinessDate())
                .branchId(cc.getBranchId())
                .branchCode(row.branch.getBranchCode())
                .branchName(row.branch.getBranchName())
                .shiftTypeCode(row.shiftType.getShiftCode())
                .shiftName(row.shiftType.getShiftName())
                .submittedByName(fullName(row.submitter))
                .submittedAt(row.submittedAt())
                .billIssueAmount(row.billIssueAmount)
                .unexplainedDiff(row.dayUnexplainedDiff)
                .totalCashIssueAmount(row.totalCashIssueAmount)
                .totalExpense(row.totalExpense)
                .withdrawalAmount(row.withdrawal)
                .status(cc.getStatus().name())
                .riskLevel(row.riskLevel.name())
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
        return Math.clamp(score, 0, 100);
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

    private ReportScopeDTO buildScope(UUID branchId, DateRange range) {
        String label = ALL_BRANCHES_LABEL;
        if (branchId != null) {
            Branch branch = entityManager.find(Branch.class, branchId);
            if (branch != null) label = branch.getBranchName();
        }
        return ReportScopeDTO.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .branchId(branchId)
                .branchLabel(label)
                .totalDays(ChronoUnit.DAYS.between(range.from(), range.to()) + 1)
                .build();
    }

    /** {@code BranchAccessGuard.branches} already returns only active branches. */
    private long branchCount(UUID branchId, long totalShiftClose, Set<UUID> reportableBranchIds) {
        if (branchId != null) {
            return totalShiftClose > 0 ? 1 : 0;
        }
        return reportableBranchIds.size();
    }

    /** "Today" and the report period are in the business's own timezone. */
    private ZoneId businessZone() {
        Business business = entityManager.find(Business.class, TenantContext.current().businessId());
        if (business == null) throw CashCloseExceptions.validationFailed("Business is unavailable");
        return ZoneId.of(business.getTimezone());
    }

    private static void requireNotInFuture(LocalDate date, String fieldName, LocalDate today) {
        if (date != null && date.isAfter(today)) {
            throw CashCloseExceptions.invalidFilter(
                    fieldName + " (" + date + ") cannot be in the future (today is " + today + ")");
        }
    }

    /** Batch-load identity rows by id. Cross-schema reads are what the federated entity library is for. */
    private <T> Map<UUID, T> byId(Class<T> type, String idField, Function<T, UUID> idOf, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return entityManager
                .createQuery("SELECT e FROM " + type.getSimpleName() + " e WHERE e." + idField + " IN :ids", type)
                .setParameter("ids", ids)
                .getResultList().stream()
                .collect(Collectors.toMap(idOf, Function.identity()));
    }

    private static String fullName(Staff staff) {
        if (staff == null) {
            return null;
        }
        return (Objects.toString(staff.getFirstName(), "") + " " + Objects.toString(staff.getLastName(), "")).trim();
    }

    /** VND amounts are whole numbers; the report DTOs keep dev's {@code long}. */
    private static long money(BigDecimal value) {
        return value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long sumAbs(List<Line> lines, Predicate<Line> matcher) {
        return lines.stream().filter(matcher).mapToLong(Line::absAmount).sum();
    }

    private long perShift(long total, long shiftCount) {
        return shiftCount == 0 ? 0 : Math.round((double) total / shiftCount);
    }

    private double rate(long count, long shiftCount) {
        return shiftCount == 0 ? 0 : Math.round(((double) count / shiftCount) * 1000) / 10.0;
    }

    // ----- internal types -------------------------------------------------------------------------

    /** One ledger line with the kind version it points at. */
    private record Line(CashMovement movement, MovementKind kind) {
        private String kindCode() {
            return kind.getKindCode();
        }

        private long absAmount() {
            return money(movement.absAmount());
        }
    }

    private static final class Row {
        private CashClose cashClose;
        private Branch branch;
        private ShiftType shiftType;
        private Staff submitter;
        private ZoneId zone;
        private List<Line> lines;
        private long posExpectedCash;
        private long withdrawal;
        private long countedCash;
        private long cashDiff;
        private long unexplainedDiff;
        private long totalExpense;
        private long tipsAmount;
        private long tipsInsideDrawer;
        private long billIssueAmount;
        private long operationalIssueAmount;
        private long dayUnexplainedDiff;
        private long totalCashIssueAmount;
        private long cashRemaining;
        private RiskLevel riskLevel;
        private boolean warning;
        private boolean pending;
        private boolean approved;
        private boolean late;
        private boolean morning;
        private boolean evening;

        private long tipsSeparate() {
            return tipsAmount - tipsInsideDrawer;
        }

        private UUID branchId() {
            return cashClose.getBranchId();
        }

        private LocalDate businessDate() {
            return cashClose.getBusinessDate();
        }

        private Instant submittedInstant() {
            return cashClose.getSubmittedAt();
        }

        private OffsetDateTime submittedAt() {
            Instant at = cashClose.getSubmittedAt();
            return at == null ? null : at.atZone(zone).toOffsetDateTime();
        }

        private RiskLevel riskLevel() {
            return riskLevel;
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
        private long totalTipsInsideDrawer;
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
            totalPosExpectedCash += row.posExpectedCash;
            totalCountedCash += row.countedCash;
            totalCashDiffNet += row.cashDiff;
            totalCashDiffAbs += Math.abs(row.cashDiff);
            totalWithdrawal += row.withdrawal;
            totalExpense += row.totalExpense;
            totalTips += row.tipsAmount;
            totalTipsInsideDrawer += row.tipsInsideDrawer;
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

    private record ReportContext(List<Row> rows, ReportScopeDTO scope, Set<UUID> reportableBranchIds) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
        private static DateRange resolve(LocalDate fromDate, LocalDate toDate, LocalDate today) {
            LocalDate from = fromDate != null ? fromDate : today.withDayOfMonth(1);
            LocalDate to = toDate != null ? toDate : today;
            return from.isAfter(to) ? new DateRange(to, from) : new DateRange(from, to);
        }
    }
}

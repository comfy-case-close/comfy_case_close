package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.response.DetailItemDTO;
import com.fnbx.cashclose.dto.response.KpiReportDTO;
import com.fnbx.cashclose.dto.response.RiskBreakdownDTO;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.MovementKindRepository;
import com.fnbx.cashclose.service.impl.ReportServiceImpl;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.identity.entity.ShiftType;
import com.fnbx.identity.entity.Staff;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dashboard reports ported from dev, read against the new DDL: figures from
 * v_close_calc, tips / issues from cash_movement lines by kind, REJECTED lines
 * ignored, and the day's unexplained difference carried by its last shift.
 *
 * <p>Day under test (one branch, two shifts):
 * <pre>
 *   CC-1 morning  APPROVED        30k short, fully explained by an approved UNPAID_BILL
 *   CC-2 evening  PENDING_REVIEW  150k short: 40k pending POS_ERROR, 99k REJECTED POS_ERROR,
 *                                 60k TIPS, 40k end-of-day parking; 110k unexplained -> HIGH
 * </pre>
 */
class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UUID businessId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final LocalDate day = LocalDate.now(ZONE).minusDays(1);
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TenantContext.of(businessId, UUID.randomUUID()));

        UUID morningId = UUID.randomUUID();
        UUID eveningId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Branch branch = with(new Branch(), "branchId", branchId, "branchCode", "TX", "branchName", "Tan Xa");
        ShiftType morning = with(new ShiftType(), "shiftTypeId", morningId, "shiftCode", "MORNING_CLOSE",
                "shiftName", "Sang", "sortOrder", 1);
        ShiftType evening = with(new ShiftType(), "shiftTypeId", eveningId, "shiftCode", "EVENING_CLOSE",
                "shiftName", "Toi", "sortOrder", 2);
        Staff staff = with(new Staff(), "staffId", staffId, "employeeCode", "E01",
                "firstName", "Su", "lastName", "Nguyen");
        Business business = with(new Business(), "businessId", businessId, "timezone", ZONE.getId());

        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        Instant morningAt = day.atTime(14, 0).atZone(ZONE).toInstant();
        CashClose cc1 = close(c1, "CC-1", morningId, CloseStatus.APPROVED, staffId, morningAt, 1_000_000, 200_000, false);
        CashClose cc2 = close(c2, "CC-2", eveningId, CloseStatus.PENDING_REVIEW, staffId,
                morningAt.plusSeconds(8 * 3600), 2_000_000, 0, true);
        CashCloseCalc k1 = calc(c1, 970_000, -30_000, 0, 0, 770_000);
        CashCloseCalc k2 = calc(c2, 1_850_000, -150_000, -110_000, 40_000, 1_740_000);

        MovementKind unpaid = kind(1L, "UNPAID_BILL", null, false);
        MovementKind posError = kind(2L, "POS_ERROR", null, false);
        MovementKind tips = kind(3L, "TIPS", null, true);
        MovementKind parking = kind(4L, "EOD_STAFF_PARKING", "STAFF_PARKING", true);
        List<CashMovement> lines = List.of(
                line(c1, 1L, -30_000, MovementStatus.APPROVED, "khach quen"),
                line(c2, 2L, -40_000, MovementStatus.PENDING, null),
                line(c2, 2L, -99_000, MovementStatus.REJECTED, null),
                line(c2, 3L, 60_000, MovementStatus.PENDING, null),
                line(c2, 4L, -40_000, MovementStatus.APPROVED, "gui xe"));

        CashCloseRepository closes = mock(CashCloseRepository.class);
        when(closes.findForReport(any(), any(), any(), any())).thenReturn(List.of(cc1, cc2));
        CashCloseCalcRepository calcs = mock(CashCloseCalcRepository.class);
        when(calcs.findAllById(any())).thenReturn(List.of(k1, k2));
        CashMovementRepository movements = mock(CashMovementRepository.class);
        when(movements.findByCashCloseIdIn(any())).thenReturn(lines);
        MovementKindRepository kinds = mock(MovementKindRepository.class);
        when(kinds.findAllById(any())).thenReturn(List.of(unpaid, posError, tips, parking));
        BranchAccessGuard guard = mock(BranchAccessGuard.class);
        when(guard.branches(Permission.REPORT_READ)).thenReturn(Set.of(branchId));

        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(Business.class, businessId)).thenReturn(business);
        when(entityManager.find(Branch.class, branchId)).thenReturn(branch);
        stubQuery(entityManager, Branch.class, List.of(branch));
        stubQuery(entityManager, ShiftType.class, List.of(morning, evening));
        stubQuery(entityManager, Staff.class, List.of(staff));

        service = new ReportServiceImpl(closes, calcs, movements, kinds, guard, entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void kpiReadsFiguresFromTheViewAndLinesByKind() {
        KpiReportDTO kpi = service.kpi(null, day, day);

        assertThat(kpi.getTotalShiftClose()).isEqualTo(2);
        // DDL sign: counted - POS, a shortage is negative
        assertThat(kpi.getTotalCashDiffNet()).isEqualTo(-180_000);
        assertThat(kpi.getTotalCashDiffAbs()).isEqualTo(180_000);
        // the day's unexplained is carried once, by the evening close
        assertThat(kpi.getTotalUnexplainedDiff()).isEqualTo(110_000);
        assertThat(kpi.getTotalTips()).isEqualTo(60_000);
        assertThat(kpi.getTotalTipsInsideDrawer()).isEqualTo(60_000);
        assertThat(kpi.getTotalTipsSeparate()).isZero();
        assertThat(kpi.getTotalBillIssueAmount()).isEqualTo(30_000);
        // the REJECTED 99k POS_ERROR counts as never declared
        assertThat(kpi.getTotalOperationalIssueAmount()).isEqualTo(70_000);
        assertThat(kpi.getTotalOtherOperationalIssueAmount()).isEqualTo(40_000);
        assertThat(kpi.getTotalCashIssueAmount()).isEqualTo(30_000 + 40_000 + 110_000);
        assertThat(kpi.getTotalExpense()).isEqualTo(40_000);
        assertThat(kpi.getTotalWithdrawal()).isEqualTo(200_000);
        assertThat(kpi.getWarningCount()).isEqualTo(1);
        assertThat(kpi.getPendingReviewCount()).isEqualTo(1);
        assertThat(kpi.getApprovedCount()).isEqualTo(1);
        assertThat(kpi.getLateCount()).isEqualTo(1);
        assertThat(kpi.getMorningCount()).isEqualTo(1);
        assertThat(kpi.getEveningCount()).isEqualTo(1);
        assertThat(kpi.getBranchCount()).isEqualTo(1);
    }

    @Test
    void riskIsDerivedFromSnapshotThresholds() {
        assertThat(service.riskBreakdown(null, day, day))
                .extracting(RiskBreakdownDTO::getRiskLevel)
                .containsExactlyInAnyOrder("LOW", "HIGH");
    }

    @Test
    void detailsSplitTheLedgerByKind() {
        List<DetailItemDTO> expense = service.details(null, day, day, "expense");
        assertThat(expense).singleElement().satisfies(item -> {
            assertThat(item.getLabel()).isEqualTo("STAFF_PARKING");
            assertThat(item.getNote()).isEqualTo("gui xe");
            assertThat(item.getAmount()).isEqualTo(40_000);
        });
        assertThat(service.details(null, day, day, "unpaid-bill")).singleElement()
                .extracting(DetailItemDTO::getReferenceCode).isEqualTo("CC-1");
        assertThat(service.details(null, day, day, "other-ops")).singleElement()
                .extracting(DetailItemDTO::getAmount).isEqualTo(40_000L);
        assertThat(service.details(null, day, day, "unexplained")).singleElement()
                .extracting(DetailItemDTO::getReferenceCode).isEqualTo("CC-2");
    }

    @Test
    void aBranchOutsideReportReadIsRefused() {
        assertThatThrownBy(() -> service.kpi(UUID.randomUUID(), day, day))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void futureDatesAndUnknownCategoriesAreRefused() {
        assertThatThrownBy(() -> service.kpi(null, LocalDate.now(ZONE).plusDays(2), null))
                .hasMessageContaining("cannot be in the future");
        assertThatThrownBy(() -> service.details(null, day, day, "nope"))
                .hasMessageContaining("Unknown report category");
    }

    // ----- fixtures ------------------------------------------------------------------------------

    private CashClose close(UUID id, String code, UUID shiftTypeId, CloseStatus status, UUID submittedBy,
                            Instant submittedAt, long pos, long withdrawal, boolean late) {
        return with(new CashClose(), "cashCloseId", id, "cashCloseCode", code, "businessId", businessId,
                "branchId", branchId, "shiftTypeId", shiftTypeId, "businessDate", day, "status", status,
                "submittedBy", submittedBy, "submittedAt", submittedAt,
                "posExpectedCash", money(pos), "withdrawalAmount", money(withdrawal),
                "appliedDiffAllowedAbs", money(20_000), "appliedDiffAlertAbs", money(100_000), "late", late);
    }

    private CashCloseCalc calc(UUID closeId, long counted, long difference, long unexplained,
                               long expense, long remaining) {
        return with(new CashCloseCalc(), "cashCloseId", closeId, "countedCash", money(counted),
                "cashDifference", money(difference), "unexplainedDifference", money(unexplained),
                "expenseTotal", money(expense), "cashRemaining", money(remaining));
    }

    private static MovementKind kind(Long sk, String code, String expenseCategory, boolean affectsRemaining) {
        return with(new MovementKind(), "kindSk", sk, "kindCode", code,
                "expenseCategory", expenseCategory, "affectsRemaining", affectsRemaining);
    }

    private static CashMovement line(UUID closeId, Long kindSk, long signed, MovementStatus status, String description) {
        return with(new CashMovement(), "cashCloseId", closeId, "kindSk", kindSk, "signedAmount", money(signed),
                "approvalStatus", status, "description", description);
    }

    @SuppressWarnings("unchecked")
    private static <T> void stubQuery(EntityManager entityManager, Class<T> type, List<T> result) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(result);
        when(entityManager.createQuery(anyString(), eq(type))).thenReturn(query);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    /** Sets fields directly: several entity columns are trigger-owned and have no setter. */
    private static <T> T with(T target, Object... fieldValuePairs) {
        for (int i = 0; i < fieldValuePairs.length; i += 2) {
            ReflectionTestUtils.setField(target, (String) fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
        return target;
    }
}

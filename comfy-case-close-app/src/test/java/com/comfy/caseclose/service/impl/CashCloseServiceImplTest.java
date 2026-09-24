package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.AttachmentRequest;
import com.comfy.caseclose.dto.request.CashCloseSubmitRequest;
import com.comfy.caseclose.dto.request.CashDenominationRequest;
import com.comfy.caseclose.dto.request.CashDiffExplanationRequest;
import com.comfy.caseclose.dto.request.CashMovementRequest;
import com.comfy.caseclose.dto.request.TipRequest;
import com.comfy.caseclose.dto.response.CashCloseResponseDTO;
import com.comfy.caseclose.entity.AppConfig;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.CashDiffExplanation;
import com.comfy.caseclose.entity.CashMovement;
import com.comfy.caseclose.entity.ShiftType;
import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.AppConfigRepository;
import com.comfy.caseclose.repository.ApprovalRepository;
import com.comfy.caseclose.repository.AttachmentRepository;
import com.comfy.caseclose.service.AttachmentStorageService;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashDenominationRepository;
import com.comfy.caseclose.repository.CashDiffExplanationRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.ShiftTypeRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.CustomUserDetails;
import com.comfy.caseclose.service.CashCloseSubmittedEvent;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.DiffDirection;
import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import com.comfy.caseclose.utils.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no database) for the computed-field logic in
 * {@link CashCloseServiceImpl}. These pin the behaviour of {@code computeTotals} as it is
 * actually implemented in the service — which is the single source of truth for the API,
 * and which differs in places from API_DESIGN.md.
 */
@ExtendWith(MockitoExtension.class)
class CashCloseServiceImplTest {

    @Mock private CashCloseRepository cashCloseRepository;
    @Mock private CashMovementRepository cashMovementRepository;
    @Mock private CashDiffExplanationRepository cashDiffExplanationRepository;
    @Mock private CashDenominationRepository cashDenominationRepository;
    @Mock private TipRepository tipRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppConfigRepository appConfigRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AttachmentStorageService attachmentStorageService;

    @InjectMocks private CashCloseServiceImpl service;

    private static final long CASH_CLOSE_ID = 101L;

    @BeforeEach
    void emptyChildCollectionsByDefault() {
        // Most reads default to empty; individual tests override what they need. lenient() so tests
        // that don't touch a given collection don't trip Mockito's strict-stub checks.
        lenient().when(attachmentRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of());
        lenient().when(approvalRepository.findByCashCloseIdOrderByReviewedAtDesc(CASH_CLOSE_ID)).thenReturn(List.of());
        lenient().when(cashMovementRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of());
        lenient().when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of());
        lenient().when(tipRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("getCashCloseById throws when the record does not exist")
    void getCashCloseById_notFound() {
        when(cashCloseRepository.findById(CASH_CLOSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCashCloseById(CASH_CLOSE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("With no movements, tips or explanations: cashDiff = pos - counted, cashRemaining = counted - withdrawal")
    void computedFields_bareShift() {
        CashClose cc = cashClose(5_000_000L, 4_920_000L, 2_000_000L);
        when(cashCloseRepository.findById(CASH_CLOSE_ID)).thenReturn(Optional.of(cc));

        CashCloseResponseDTO dto = service.getCashCloseById(CASH_CLOSE_ID);

        assertThat(dto.getPosExpectedCash()).isEqualTo(5_000_000L);
        assertThat(dto.getWithdrawalAmount()).isEqualTo(2_000_000L);
        assertThat(dto.getCashDiff()).isEqualTo(80_000L);          // 5,000,000 - 4,920,000
        assertThat(dto.getTotalExpense()).isEqualTo(0L);
        assertThat(dto.getEndOfDayExpenseAmount()).isEqualTo(0L);
        assertThat(dto.getTipsAmount()).isEqualTo(0L);
        assertThat(dto.getExplainedDiff()).isEqualTo(0L);
        assertThat(dto.getUnexplainedDiff()).isEqualTo(80_000L);   // cashDiff - explainedDiff
        assertThat(dto.getCashRemaining()).isEqualTo(2_920_000L);  // 4,920,000 - 2,000,000
    }

    @Test
    @DisplayName("Full shift: expense, end-of-day expense, tips and explanations flow into every computed field")
    void computedFields_fullShift() {
        CashClose cc = cashClose(5_000_000L, 4_920_000L, 2_000_000L);
        when(cashCloseRepository.findById(CASH_CLOSE_ID)).thenReturn(Optional.of(cc));

        when(cashMovementRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                movement(cc, MovementType.EXPENSE, MovementCategory.SUPPLY, 150_000L),
                movement(cc, MovementType.END_OF_DAY_EXPENSE, MovementCategory.STAFF_PARKING, 20_000L)));
        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(cc, DiffReasonType.UNPAID_BILL, DiffDirection.SHORTAGE, 80_000L),
                explanation(cc, DiffReasonType.TIPS_IN_CASH_DRAWER, DiffDirection.SURPLUS, -50_000L)));
        when(tipRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(tip(cc, 50_000L)));

        CashCloseResponseDTO dto = service.getCashCloseById(CASH_CLOSE_ID);

        assertThat(dto.getCashDiff()).isEqualTo(80_000L);           // 5,000,000 - 4,920,000
        assertThat(dto.getTotalExpense()).isEqualTo(170_000L);      // 150,000 + 20,000
        assertThat(dto.getEndOfDayExpenseAmount()).isEqualTo(20_000L);
        assertThat(dto.getTipsAmount()).isEqualTo(50_000L);
        assertThat(dto.getExplainedDiff()).isEqualTo(30_000L);      // 80,000 + (-50,000)
        assertThat(dto.getUnexplainedDiff()).isEqualTo(50_000L);    // 80,000 - 30,000
        // 4,920,000 - withdrawal 2,000,000 - endOfDay 20,000 — tips never leave counted cash
        assertThat(dto.getCashRemaining()).isEqualTo(2_900_000L);
        assertThat(dto.getTipsInsideDrawerAmount()).isEqualTo(50_000L);
        assertThat(dto.getTipsSeparateAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("A surplus (counted > expected) yields a negative cashDiff")
    void computedFields_surplus() {
        CashClose cc = cashClose(4_000_000L, 4_030_000L, 0L);
        when(cashCloseRepository.findById(CASH_CLOSE_ID)).thenReturn(Optional.of(cc));

        CashCloseResponseDTO dto = service.getCashCloseById(CASH_CLOSE_ID);

        assertThat(dto.getCashDiff()).isEqualTo(-30_000L);
        assertThat(dto.getUnexplainedDiff()).isEqualTo(-30_000L);
        assertThat(dto.getCashRemaining()).isEqualTo(4_030_000L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("submitCashClose auto-files a SHORTAGE explanation for an EXPENSE movement, "
            + "using the reason the shift lead picked on the row (database.md cash_movements rule)")
    void submitCashClose_autoExplainsExpenseMovement() {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("TX");

        ShiftType shiftType = new ShiftType();
        shiftType.setId(11L);
        shiftType.setShiftTypeCode("EVENING_CLOSE");
        shiftType.setSortOrder((short) 2);

        User user = new User();
        user.setId(42L);
        user.setFullName("Tâm Trưởng Ca");
        user.setRole(UserRole.STAFF);

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(shiftTypeRepository.findById(11L)).thenReturn(Optional.of(shiftType));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(cashCloseRepository.findActiveByBranchAndDate(eq(1L), any())).thenReturn(List.of());
        when(cashCloseRepository.save(any(CashClose.class))).thenAnswer(invocation -> {
            CashClose saved = invocation.getArgument(0);
            saved.setId(CASH_CLOSE_ID);
            return saved;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CustomUserDetails.from(user), null, List.of()));

        CashCloseSubmitRequest request = new CashCloseSubmitRequest();
        request.setBranchId(1L);
        request.setShiftTypeId(11L);
        request.setBusinessDate(LocalDate.of(2026, 8, 17));
        request.setPosExpectedCash(5_000_000L);
        request.setCountedCash(4_850_000L);
        request.setWithdrawalAmount(0L);

        CashMovementRequest expense = new CashMovementRequest();
        expense.setCategory("SUPPLY");
        expense.setType("EXPENSE");
        expense.setAmount(150_000L);
        expense.setReason("SUPPLY_NOT_IN_POS");
        expense.setDescription("Mua giấy in bill");
        request.setMovements(List.of(expense));

        service.submitCashClose(request);

        ArgumentCaptor<CashDiffExplanation> captor = ArgumentCaptor.forClass(CashDiffExplanation.class);
        verify(cashDiffExplanationRepository).save(captor.capture());
        CashDiffExplanation saved = captor.getValue();
        assertThat(saved.getReasonType()).isEqualTo(DiffReasonType.SUPPLY_NOT_IN_POS);
        assertThat(saved.getDirection()).isEqualTo(DiffDirection.SHORTAGE);
        assertThat(saved.getSignedAmount()).isEqualTo(150_000L);
    }

    /**
     * Regression test for a bug hunt finding: this guard used to exist only on the frontend
     * (cash-close-math.ts's 'withdrawalExceedsCounted'), so a request built outside the form could
     * withdraw more than was ever counted. The FE hard-blocks it; the server must too, not just
     * flag it for review after the fact.
     */
    @Test
    @DisplayName("submitCashClose rejects a withdrawal larger than the counted cash")
    void submitCashClose_rejectsWithdrawalExceedingCountedCash() {
        mockBranchShiftUser();

        CashCloseSubmitRequest request = new CashCloseSubmitRequest();
        request.setBranchId(1L);
        request.setShiftTypeId(11L);
        request.setBusinessDate(LocalDate.of(2026, 8, 17));
        request.setPosExpectedCash(5_000_000L);
        request.setCountedCash(1_000_000L);
        request.setWithdrawalAmount(1_500_000L); // more than was counted

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot exceed counted cash");
    }

    /**
     * Regression test for a bug hunt finding: a negative till used to only add +4 to the risk
     * score (CashCloseServiceImpl#assessRisk) — enough to reach PENDING_REVIEW on its own, but not
     * a rejection, and not guaranteed if config thresholds ever changed the scoring. This mirrors
     * the frontend's 'cashRemainingNegative' hard block. Tips are deliberately not part of it any
     * more: they never leave counted cash, so end-of-day spend is what can push the till below zero.
     */
    @Test
    @DisplayName("submitCashClose rejects a negative cash remaining after withdrawal and end-of-day spend")
    void submitCashClose_rejectsNegativeCashRemaining() {
        mockBranchShiftUser();

        CashCloseSubmitRequest request = baseRequest(1_000_000L, 1_000_000L, 900_000L); // within countedCash on its own

        // computeTotals reads movements back through the repository, not the request DTO — the
        // @BeforeEach default (empty list) would otherwise mask the very variance under test.
        when(cashMovementRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                movement(anyClose(), MovementType.END_OF_DAY_EXPENSE, MovementCategory.STAFF_PARKING, 200_000L)));
        // 1,000,000 - 900,000 - 200,000 = -100,000 remaining

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cash remaining");
    }

    @Test
    @DisplayName("Tips do not reduce cash remaining, whichever way they are booked")
    void tips_neverReduceCashRemaining() {
        mockBranchShiftUser();
        // 1,000,000 counted, 900,000 withdrawn: 200,000 of tips used to push this to -100,000 and be rejected.
        CashCloseSubmitRequest request = baseRequest(1_000_000L, 1_000_000L, 900_000L);
        TipRequest tip = new TipRequest();
        tip.setAmount(200_000L);
        request.setTips(tip);
        when(tipRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(tip(null, 200_000L, false)));

        CashCloseResponseDTO dto = service.submitCashClose(request);

        assertThat(dto.getCashRemaining()).isEqualTo(100_000L);
        assertThat(dto.getTipsAmount()).isEqualTo(200_000L);
        assertThat(dto.getTipsSeparateAmount()).isEqualTo(200_000L);
        assertThat(dto.getTipsInsideDrawerAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("A separate tip files no explanation; a tip merged into the drawer files an offsetting surplus")
    void persistTip_onlyExplainsTipsMergedIntoTheDrawer() {
        mockBranchShiftUser();

        CashCloseSubmitRequest separate = baseRequest(1_000_000L, 1_000_000L, 0L);
        TipRequest separateTip = new TipRequest();
        separateTip.setAmount(10_000L); // isInsideCashDrawer defaults to false = tách két
        separate.setTips(separateTip);
        service.submitCashClose(separate);
        verify(cashDiffExplanationRepository, never()).save(any(CashDiffExplanation.class));

        CashCloseSubmitRequest merged = baseRequest(1_000_000L, 1_010_000L, 0L);
        TipRequest mergedTip = new TipRequest();
        mergedTip.setAmount(10_000L);
        mergedTip.setIsInsideCashDrawer(true);
        merged.setTips(mergedTip);
        service.submitCashClose(merged);

        ArgumentCaptor<CashDiffExplanation> captor = ArgumentCaptor.forClass(CashDiffExplanation.class);
        verify(cashDiffExplanationRepository).save(captor.capture());
        assertThat(captor.getValue().getReasonType()).isEqualTo(DiffReasonType.TIPS_IN_CASH_DRAWER);
        assertThat(captor.getValue().getSignedAmount()).isEqualTo(-10_000L);
    }

    // ----- validation ----------------------------------------------------------------------------

    @Test
    @DisplayName("submitCashClose rejects a business date in the future")
    void submitCashClose_rejectsFutureBusinessDate() {
        mockBranchShiftUser();
        CashCloseSubmitRequest request = baseRequest(1_000_000L, 1_000_000L, 0L);
        request.setBusinessDate(LocalDate.now().plusDays(2));

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cash remaining");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submitCashClose_publishesBranchAndShiftSnapshot() {
        mockBranchShiftUser();
        CashCloseSubmitRequest request = new CashCloseSubmitRequest();
        request.setBranchId(1L);
        request.setShiftTypeId(11L);
        request.setBusinessDate(LocalDate.of(2026, 9, 14));
        request.setPosExpectedCash(1_000_000L);
        request.setCountedCash(1_000_000L);
        request.setWithdrawalAmount(0L);

        CashCloseResponseDTO response = service.submitCashClose(request);

        var event = ArgumentCaptor.forClass(CashCloseSubmittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().cashCloseId()).isEqualTo(response.getId());
        assertThat(event.getValue().branchId()).isEqualTo(1L);
        assertThat(event.getValue().branchCode()).isEqualTo("TX");
        assertThat(event.getValue().shiftTypeCode()).isEqualTo("EVENING_CLOSE");
        assertThat(event.getValue().submittedBy()).isEqualTo("Tâm Trưởng Ca");
    }

    @Test
    @DisplayName("submitCashClose rejects fractional, negative and repeated denomination rows")
    void submitCashClose_rejectsInvalidDenominations() {
        mockBranchShiftUser();

        CashCloseSubmitRequest fractional = baseRequest(15_000L, 15_000L, 0L);
        fractional.setDenominations(List.of(denomination(10_000L, "1.5")));
        assertThatThrownBy(() -> service.submitCashClose(fractional))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("whole number");

        CashCloseSubmitRequest negative = baseRequest(0L, 0L, 0L);
        negative.setDenominations(List.of(denomination(10_000L, "-1")));
        assertThatThrownBy(() -> service.submitCashClose(negative))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negative");

        CashCloseSubmitRequest repeated = baseRequest(20_000L, 20_000L, 0L);
        repeated.setDenominations(List.of(denomination(10_000L, "1"), denomination(10_000L, "1")));
        assertThatThrownBy(() -> service.submitCashClose(repeated))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    @DisplayName("submitCashClose rejects the same explanation entered twice")
    void submitCashClose_rejectsDuplicateExplanation() {
        mockBranchShiftUser();
        CashCloseSubmitRequest request = baseRequest(1_000_000L, 900_000L, 0L);
        request.setExplanations(List.of(
                explanationRequest("MISCOUNT", 50_000L, "đếm nhầm"),
                explanationRequest("MISCOUNT", 50_000L, "đếm nhầm")));

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate explanation");
    }

    @Test
    @DisplayName("submitCashClose rejects a hand-typed TIPS_IN_CASH_DRAWER explanation")
    void submitCashClose_rejectsManualTipsExplanation() {
        mockBranchShiftUser();
        CashCloseSubmitRequest request = baseRequest(1_000_000L, 1_010_000L, 0L);
        request.setExplanations(List.of(explanationRequest("TIPS_IN_CASH_DRAWER", -10_000L, null)));

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("TIPS_IN_CASH_DRAWER");
    }

    @Test
    @DisplayName("submitCashClose rejects explanations that add up to more than the cash difference")
    void submitCashClose_rejectsOverExplainedDiff() {
        mockBranchShiftUser();
        // diff = 150,000 short; the expense auto-explains all of it and a manual line explains it again.
        CashCloseSubmitRequest request = baseRequest(5_000_000L, 4_850_000L, 0L);
        request.setMovements(List.of(expenseRequest(150_000L, "SUPPLY_NOT_IN_POS")));
        request.setExplanations(List.of(explanationRequest("SUPPLY_NOT_IN_POS", 150_000L, "mua giấy")));

        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(anyClose(), DiffReasonType.SUPPLY_NOT_IN_POS, DiffDirection.SHORTAGE, 150_000L),
                explanation(anyClose(), DiffReasonType.SUPPLY_NOT_IN_POS, DiffDirection.SHORTAGE, 150_000L)));

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceed the cash difference");
    }

    @Test
    @DisplayName("Explanations that exactly cover the diff, or offset each other, are accepted")
    void submitCashClose_acceptsExactAndOffsettingExplanations() {
        mockBranchShiftUser();
        CashCloseSubmitRequest exact = baseRequest(1_000_000L, 950_000L, 0L);
        exact.setExplanations(List.of(explanationRequest("MISCOUNT", 50_000L, null)));
        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(anyClose(), DiffReasonType.MISCOUNT, DiffDirection.SHORTAGE, 50_000L)));

        assertThat(service.submitCashClose(exact).getUnexplainedDiff()).isEqualTo(0L);

        // No diff at all: 30,000 short explained one way and 30,000 over explained another nets to zero.
        CashCloseSubmitRequest offsetting = baseRequest(1_000_000L, 1_000_000L, 0L);
        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(anyClose(), DiffReasonType.MISCOUNT, DiffDirection.SHORTAGE, 30_000L),
                explanation(anyClose(), DiffReasonType.POS_ERROR, DiffDirection.SURPLUS, -30_000L)));

        assertThat(service.submitCashClose(offsetting).getUnexplainedDiff()).isEqualTo(0L);
    }

    @Test
    @DisplayName("A close with nothing to explain rejects an explanation that invents a diff")
    void submitCashClose_rejectsExplanationWhenThereIsNoDiff() {
        mockBranchShiftUser();
        CashCloseSubmitRequest request = baseRequest(1_000_000L, 1_000_000L, 0L);
        request.setExplanations(List.of(explanationRequest("MISCOUNT", 50_000L, null)));
        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(anyClose(), DiffReasonType.MISCOUNT, DiffDirection.SHORTAGE, 50_000L)));

        assertThatThrownBy(() -> service.submitCashClose(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceed the cash difference");
    }

    @Test
    @DisplayName("An unpaid-bill explanation needs a repayment proof photo when the config requires it")
    void submitCashClose_requiresBillRepaymentProof() {
        mockBranchShiftUser();
        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(config(true)));

        CashCloseSubmitRequest withoutProof = baseRequest(1_000_000L, 920_000L, 0L);
        withoutProof.setExplanations(List.of(explanationRequest("UNPAID_BILL", 80_000L, "quên bill bàn 5")));
        // A photo of the wrong kind (or none) doesn't count.
        withoutProof.setAttachments(List.of(attachment("POS_RECEIPT", "https://files/pos.jpg")));

        assertThatThrownBy(() -> service.submitCashClose(withoutProof))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("UNPAID_BILL_REPAYMENT_PROOF");

        // An expense row filed under UNPAID_BILL auto-explains as one, so it needs the proof too.
        CashCloseSubmitRequest expenseWithoutProof = baseRequest(1_000_000L, 920_000L, 0L);
        expenseWithoutProof.setMovements(List.of(expenseRequest(80_000L, "UNPAID_BILL")));
        assertThatThrownBy(() -> service.submitCashClose(expenseWithoutProof))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("UNPAID_BILL_REPAYMENT_PROOF");
    }

    @Test
    @DisplayName("An unpaid-bill explanation passes with the proof photo, or when the config doesn't require it")
    void submitCashClose_acceptsBillWithProofOrWhenNotRequired() {
        mockBranchShiftUser();
        when(cashDiffExplanationRepository.findByCashCloseId(CASH_CLOSE_ID)).thenReturn(List.of(
                explanation(anyClose(), DiffReasonType.UNPAID_BILL, DiffDirection.SHORTAGE, 80_000L)));

        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(config(true)));
        CashCloseSubmitRequest withProof = baseRequest(1_000_000L, 920_000L, 0L);
        withProof.setExplanations(List.of(explanationRequest("UNPAID_BILL", 80_000L, "quên bill bàn 5")));
        withProof.setAttachments(List.of(attachment("UNPAID_BILL_REPAYMENT_PROOF", "https://files/proof.jpg")));
        assertThat(service.submitCashClose(withProof).getUnexplainedDiff()).isEqualTo(0L);

        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(config(false)));
        CashCloseSubmitRequest notRequired = baseRequest(1_000_000L, 920_000L, 0L);
        notRequired.setExplanations(List.of(explanationRequest("UNPAID_BILL", 80_000L, "quên bill bàn 5")));
        assertThat(service.submitCashClose(notRequired).getUnexplainedDiff()).isEqualTo(0L);
    }

    // ----- fixtures ------------------------------------------------------------------------------

    /** Shared branch/shiftType/user + security-context setup for the submitCashClose tests. */
    private void mockBranchShiftUser() {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("TX");

        ShiftType shiftType = new ShiftType();
        shiftType.setId(11L);
        shiftType.setShiftTypeCode("EVENING_CLOSE");
        shiftType.setSortOrder((short) 2);

        User user = new User();
        user.setId(42L);
        user.setFullName("Tâm Trưởng Ca");
        user.setRole(UserRole.STAFF);

        lenient().when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        lenient().when(shiftTypeRepository.findById(11L)).thenReturn(Optional.of(shiftType));
        lenient().when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        lenient().when(cashCloseRepository.findActiveByBranchAndDate(eq(1L), any())).thenReturn(List.of());
        lenient().when(cashCloseRepository.save(any(CashClose.class))).thenAnswer(invocation -> {
            CashClose saved = invocation.getArgument(0);
            saved.setId(CASH_CLOSE_ID);
            return saved;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CustomUserDetails.from(user), null, List.of()));
    }

    private CashClose cashClose(long posExpected, long counted, long withdrawal) {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("TX");

        ShiftType shiftType = new ShiftType();
        shiftType.setId(11L);
        shiftType.setShiftTypeCode("EVENING_CLOSE");
        shiftType.setShiftName("Kết ca tối");
        shiftType.setSortOrder((short) 2);

        User user = new User();
        user.setId(42L);
        user.setFullName("Tâm Trưởng Ca");

        CashClose cc = new CashClose();
        cc.setId(CASH_CLOSE_ID);
        cc.setReferenceCode("CC-TEST-0001");
        cc.setBranch(branch);
        cc.setShiftType(shiftType);
        cc.setSubmittedBy(user);
        cc.setPosExpectedCash(posExpected);
        cc.setCountedCash(counted);
        cc.setWithdrawalAmount(withdrawal);
        cc.setStatus(CashCloseStatus.SUBMITTED);
        cc.setRiskLevel(RiskLevel.LOW);
        cc.setIsLate(false);
        return cc;
    }

    private CashMovement movement(CashClose cc, MovementType type, MovementCategory category, long amount) {
        CashMovement m = new CashMovement();
        m.setCashClose(cc);
        m.setMovementType(type);
        m.setCategory(category);
        m.setAmount(amount);
        return m;
    }

    private CashDiffExplanation explanation(CashClose cc, DiffReasonType reason, DiffDirection direction, long signed) {
        CashDiffExplanation e = new CashDiffExplanation();
        e.setCashClose(cc);
        e.setReasonType(reason);
        e.setDirection(direction);
        e.setSignedAmount(signed);
        return e;
    }

    private Tip tip(CashClose cc, long amount) {
        return tip(cc, amount, true);
    }

    private Tip tip(CashClose cc, long amount, boolean insideDrawer) {
        Tip t = new Tip();
        t.setCashClose(cc);
        t.setAmount(amount);
        t.setIsInsideCashDrawer(insideDrawer);
        return t;
    }

    /** Response mapping dereferences each child's cashClose, so stubbed children need a real parent. */
    private CashClose anyClose() {
        return cashClose(0L, 0L, 0L);
    }

    /** A minimal valid submit request for a bare shift; tests add the one thing they exercise. */
    private CashCloseSubmitRequest baseRequest(long posExpected, long counted, long withdrawal) {
        CashCloseSubmitRequest request = new CashCloseSubmitRequest();
        request.setBranchId(1L);
        request.setShiftTypeId(11L);
        request.setBusinessDate(LocalDate.of(2026, 8, 17));
        request.setPosExpectedCash(posExpected);
        request.setCountedCash(counted);
        request.setWithdrawalAmount(withdrawal);
        return request;
    }

    private CashDenominationRequest denomination(long value, String quantity) {
        CashDenominationRequest row = new CashDenominationRequest();
        row.setDenominationValue(value);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    private CashDiffExplanationRequest explanationRequest(String reason, long signedAmount, String notes) {
        return new CashDiffExplanationRequest(reason, signedAmount, notes);
    }

    private CashMovementRequest expenseRequest(long amount, String reason) {
        return new CashMovementRequest("SUPPLY", "EXPENSE", amount, reason, "chi trong ca");
    }

    private AttachmentRequest attachment(String type, String url) {
        return new AttachmentRequest(type, url, "file.jpg", null);
    }

    private AppConfig config(boolean requireBillProof) {
        AppConfig config = new AppConfig();
        config.setRequireUnpaidBillRepayment(requireBillProof);
        return config;
    }
}

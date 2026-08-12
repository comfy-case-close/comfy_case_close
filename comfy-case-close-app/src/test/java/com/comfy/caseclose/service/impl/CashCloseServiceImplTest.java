package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.response.CashCloseResponseDTO;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.CashDiffExplanation;
import com.comfy.caseclose.entity.CashMovement;
import com.comfy.caseclose.entity.ShiftType;
import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.ApprovalRepository;
import com.comfy.caseclose.repository.AttachmentRepository;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashDenominationRepository;
import com.comfy.caseclose.repository.CashDiffExplanationRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.ShiftTypeRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.DiffDirection;
import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
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
        // 4,920,000 - withdrawal 2,000,000 - tips 50,000 - endOfDay 20,000
        assertThat(dto.getCashRemaining()).isEqualTo(2_850_000L);
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

    // ----- fixtures ------------------------------------------------------------------------------

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
        Tip t = new Tip();
        t.setCashClose(cc);
        t.setAmount(amount);
        t.setIsInsideCashDrawer(true);
        return t;
    }
}

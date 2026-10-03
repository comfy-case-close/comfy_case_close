package com.fnbx.cashclose;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseDecision;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.entity.CashMovementDecision;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.repository.CashCloseDecisionRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.CashMovementDecisionRepository;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.service.EffectiveConfig;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashCloseApprovalConfigTest {
    @Mock CashCloseRepository closes;
    @Mock CashCloseCalcRepository calculations;
    @Mock CashMovementRepository movements;
    @Mock CashMovementDecisionRepository movementDecisions;
    @Mock CashCloseMapper mapper;
    @Mock CashCloseDecisionRepository decisions;
    @Mock BranchAccessGuard access;
    @Mock EffectiveConfig config;
    @Mock com.fnbx.cashclose.service.FundWithdrawalService fundWithdrawals;
    @Mock EntityManager entityManager;
    @InjectMocks CashCloseServiceImpl service;

    private final UUID business = UUID.randomUUID(), branch = UUID.randomUUID(), manager = UUID.randomUUID();
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void unconfirmedWithdrawalBlocksCloseBeforeMovementApproval() {
        TenantContext.set(TenantContext.of(business, manager));
        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID()); close.setBranchId(branch); close.setBusinessId(business);
        close.setStatus(CloseStatus.SUBMITTED);
        when(closes.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        doThrow(new AppException(ErrorCode.WITHDRAWAL_CONFIRMATION_REQUIRED))
                .when(fundWithdrawals).requireConfirmed(close);
        assertThatThrownBy(() -> service.approve(branch, close.getCashCloseId(), null))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WITHDRAWAL_CONFIRMATION_REQUIRED));
        assertThat(close.getStatus()).isEqualTo(CloseStatus.SUBMITTED);
        verifyNoInteractions(movements, decisions);
    }

    @Test void requiredRefundMustReceiveSeparateDecision() {
        var fixture = fixture("REFUND");
        doReturn(true).when(config).bool(branch, "REQUIRE_APPROVAL_REFUND", false);
        assertThatThrownBy(() -> service.approve(branch, fixture.close().getCashCloseId(), null))
                .isInstanceOfSatisfying(AppException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MOVEMENTS_PENDING);
                            assertThat(e.getMessage()).contains("EXPENSE_REFUND");
                        });
        assertThat(fixture.line().getApprovalStatus()).isEqualTo(MovementStatus.PENDING);
        verifyNoInteractions(decisions);
    }

    @Test void listsEveryRequiredTypeBeforeApprovingOptionalMovements() {
        var fixture = fixture("STAFF_PARKING");
        CashMovement refund = new CashMovement(); refund.setKindSk(2L);
        CashMovement tips = new CashMovement(); tips.setKindSk(3L);
        MovementKind refundKind = new MovementKind();
        refundKind.setKindCode("EXPENSE_REFUND"); refundKind.setExpenseCategory("REFUND");
        MovementKind tipsKind = new MovementKind(); tipsKind.setKindCode("TIPS");
        when(movements.findByCashCloseIdAndApprovalStatus(fixture.close().getCashCloseId(), MovementStatus.PENDING))
                .thenReturn(List.of(fixture.line(), refund, tips));
        when(entityManager.find(MovementKind.class, 2L)).thenReturn(refundKind);
        when(entityManager.find(MovementKind.class, 3L)).thenReturn(tipsKind);
        doReturn(false).when(config).bool(branch, "REQUIRE_APPROVAL_STAFF_PARKING", false);
        doReturn(true).when(config).bool(branch, "REQUIRE_APPROVAL_REFUND", false);

        assertThatThrownBy(() -> service.approve(branch, fixture.close().getCashCloseId(), null))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MOVEMENTS_PENDING);
                    assertThat(e.getMessage()).contains("EXPENSE_REFUND, TIPS");
                    assertThat(e.getMessage()).doesNotContain("EOD_STAFF_PARKING");
                });
        assertThat(fixture.close().getStatus()).isEqualTo(CloseStatus.SUBMITTED);
        verifyNoInteractions(movementDecisions, decisions);
    }

    @Test void optionalParkingIsDecidedWithTheCloseAndAudited() {
        var fixture = fixture("STAFF_PARKING");
        when(config.bool(branch, "REQUIRE_APPROVAL_STAFF_PARKING", false)).thenReturn(false);
        when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementDecisions.saveAndFlush(any())).thenAnswer(invocation -> {
            CashMovementDecision decision = invocation.getArgument(0);
            fixture.line().setApprovalStatus(decision.getNewStatus());
            return decision;
        });

        service.approve(branch, fixture.close().getCashCloseId(), null);

        assertThat(fixture.line().getApprovalStatus()).isEqualTo(MovementStatus.APPROVED);
        assertThat(fixture.close().getStatus()).isEqualTo(CloseStatus.APPROVED);
        var captured = org.mockito.ArgumentCaptor.forClass(CashMovementDecision.class);
        verify(movementDecisions).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getDecidedBy()).isEqualTo(manager);
        assertThat(captured.getValue().getAction()).isEqualTo(com.fnbx.cashclose.enums.MovementAction.APPROVE);
        verify(entityManager, atLeastOnce()).flush();
        verify(decisions).save(any(CashCloseDecision.class));
    }

    private Fixture fixture(String category) {
        TenantContext.set(TenantContext.of(business, manager));
        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBusinessId(business);
        close.setBranchId(branch);
        close.setStatus(CloseStatus.SUBMITTED);
        CashMovement line = new CashMovement();
        line.setMovementId(UUID.randomUUID());
        line.setKindSk(1L);
        MovementKind kind = new MovementKind();
        kind.setKindCode(category.equals("STAFF_PARKING") ? "EOD_STAFF_PARKING" : "EXPENSE_" + category);
        kind.setExpenseCategory(category);
        when(closes.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        when(movements.findByCashCloseIdAndApprovalStatus(close.getCashCloseId(), MovementStatus.PENDING))
                .thenReturn(List.of(line));
        when(entityManager.find(MovementKind.class, 1L)).thenReturn(kind);
        return new Fixture(close, line);
    }

    private record Fixture(CashClose close, CashMovement line) {}
}

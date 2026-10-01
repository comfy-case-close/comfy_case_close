package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.*;
import com.fnbx.cashclose.entity.*;
import com.fnbx.cashclose.enums.*;
import com.fnbx.cashclose.repository.FundWithdrawalRepository;
import com.fnbx.cashclose.repository.FundWithdrawalDecisionRepository;
import com.fnbx.cashclose.service.impl.FundWithdrawalServiceImpl;
import com.fnbx.identity.entity.Branch;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FundWithdrawalServiceTest {
    final UUID businessId=UUID.randomUUID(), branchId=UUID.randomUUID(), recorder=UUID.randomUUID(), manager=UUID.randomUUID();
    final Instant time=Instant.parse("2026-01-01T10:00:00Z");
    final FundWithdrawalRepository repository=mock(FundWithdrawalRepository.class);
    final FundWithdrawalDecisionRepository decisions=mock(FundWithdrawalDecisionRepository.class);
    final BranchAccessGuard guard=mock(BranchAccessGuard.class);
    final EntityManager em=mock(EntityManager.class);
    final com.fnbx.cashclose.service.EffectiveConfig config=mock(com.fnbx.cashclose.service.EffectiveConfig.class);
    final FundWithdrawalServiceImpl service=new FundWithdrawalServiceImpl(repository,decisions,guard,em,config);
    final CashClose close=new CashClose();
    @BeforeEach void setup() {
        TenantContext.set(TenantContext.of(businessId,recorder));
        when(config.number(any(),anyString(),any())).thenReturn(BigDecimal.ZERO);
        close.setCashCloseId(UUID.randomUUID()); close.setBusinessId(businessId); close.setBranchId(branchId);
        close.setWithdrawalAmount(new BigDecimal("1000000"));
        when(repository.findByCashCloseId(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        // Simulate the database applying a decision; the integration suite exercises the real trigger.
        when(decisions.saveAndFlush(any())).thenAnswer(i -> {
            FundWithdrawalDecision d=i.getArgument(0);
            var w=repository.findById(d.getFundWithdrawalId()).orElseThrow();
            if(d.getAction()==FundWithdrawalAction.EDIT) {
                @SuppressWarnings("unchecked") var after=(Map<String,Object>)d.getChanges().get("after");
                w.setAmount((BigDecimal)after.get("amount"));
                w.setWithdrawnBy(UUID.fromString((String)after.get("withdrawnBy")));
                w.setWithdrawnAt(Instant.parse((String)after.get("withdrawnAt")));
                w.setNote((String)after.get("note"));
            }
            w.setStatus(d.getNewStatus());
            return d;
        });
        Branch branch=new Branch(); branch.setBusinessId(businessId); branch.setBranchId(branchId);
        when(em.find(Branch.class,branchId)).thenReturn(branch);
        Query q=mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(),any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(1L);
    }
    @AfterEach void clear() { TenantContext.clear(); }
    CashCloseFiguresRequest figures() {
        return CashCloseFiguresRequest.builder().withdrawalAmount(close.getWithdrawalAmount())
                .withdrawnBy(manager).withdrawnAt(time).build();
    }
    FundWithdrawal existing(FundStatus status) {
        FundWithdrawal w=new FundWithdrawal(); UUID id=UUID.randomUUID();
        w.setFundWithdrawalId(id);
        w.setBusinessId(businessId); w.setBranchId(branchId); w.setCashCloseId(close.getCashCloseId());
        w.setAmount(close.getWithdrawalAmount()); w.setWithdrawnBy(manager); w.setWithdrawnAt(time);
        w.setFromPot(CashPot.DRAWER); w.setToPot(CashPot.BRANCH_SAFE); w.setStatus(status);
        when(repository.findByCashCloseId(close.getCashCloseId())).thenReturn(Optional.of(w));
        when(repository.findById(id)).thenReturn(Optional.of(w));
        when(em.find(FundWithdrawal.class,id)).thenReturn(w);
        when(em.find(CashClose.class,close.getCashCloseId())).thenReturn(close);
        return w;
    }
    @Test void submissionCreatesPendingTransferWithDistinctRecorderAndWithdrawer() {
        service.syncCloseWithdrawal(close,figures(),null);
        verify(repository).saveAndFlush(argThat(w->w.getStatus()==FundStatus.PENDING
            && w.getCashCloseId().equals(close.getCashCloseId()) && w.getWithdrawnBy().equals(manager)
            && w.getRecordedBy().equals(recorder)
            && w.getFromPot()==CashPot.DRAWER && w.getToPot()==CashPot.BRANCH_SAFE
            && w.getAmount().compareTo(new BigDecimal("1000000"))==0));
    }
    @Test void zeroSubmissionNeedsNoTransferOrConfirmation() {
        close.setWithdrawalAmount(BigDecimal.ZERO);
        service.syncCloseWithdrawal(close, CashCloseFiguresRequest.builder().withdrawalAmount(BigDecimal.ZERO).build(),null);
        service.requireConfirmed(close);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void positiveSubmissionRequiresPersonAndTime() {
        assertThatThrownBy(()->service.syncCloseWithdrawal(close,
            CashCloseFiguresRequest.builder().withdrawalAmount(BigDecimal.ONE).build(),null)).isInstanceOf(AppException.class);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void inactiveOrUnassignedPersonIsRejected() {
        when(em.createNativeQuery(anyString()).getSingleResult()).thenReturn(0L);
        assertThatThrownBy(()->service.syncCloseWithdrawal(close,figures(),null)).isInstanceOf(AppException.class);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void onlyNamedPersonCanConfirm() {
        FundWithdrawal w=existing(FundStatus.PENDING);
        assertThatThrownBy(()->service.confirm(branchId,w.getFundWithdrawalId())).isInstanceOf(AccessDeniedException.class);
        assertThat(w.getStatus()).isEqualTo(FundStatus.PENDING);
        TenantContext.set(TenantContext.of(businessId,manager));
        assertThat(service.confirm(branchId,w.getFundWithdrawalId()).status()).isEqualTo(FundStatus.CONFIRMED);
        service.confirm(branchId,w.getFundWithdrawalId()); // retry does not append twice
        verify(decisions).saveAndFlush(argThat(d -> d.getActedBy().equals(manager)
            && d.getAction()==FundWithdrawalAction.CONFIRM && d.getOldStatus()==FundStatus.PENDING
            && d.getNewStatus()==FundStatus.CONFIRMED && d.getFundWithdrawalId().equals(w.getFundWithdrawalId())));
        service.requireConfirmed(close);
    }
    @Test void onlyNamedPersonCanRejectAndReasonIsRecordedInDecision() {
        var w=existing(FundStatus.PENDING);
        assertThatThrownBy(()->service.reject(branchId,w.getFundWithdrawalId(),"Wrong amount"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(decisions);
        TenantContext.set(TenantContext.of(businessId,manager));
        assertThat(service.reject(branchId,w.getFundWithdrawalId(),"  Wrong amount  ").status())
                .isEqualTo(FundStatus.REJECTED);
        verify(decisions).saveAndFlush(argThat(d -> d.getActedBy().equals(manager)
            && d.getAction()==FundWithdrawalAction.REJECT && d.getNote().equals("Wrong amount")
            && d.getOldStatus()==FundStatus.PENDING && d.getNewStatus()==FundStatus.REJECTED));
    }
    @Test void decisionHistoryUsesRevisionReadAuthorization() {
        var w=existing(FundStatus.REJECTED); w.setCashCloseId(null);
        assertThatThrownBy(()->service.history(branchId,w.getFundWithdrawalId()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(decisions);
        TenantContext.set(TenantContext.of(businessId,manager));
        service.history(branchId,w.getFundWithdrawalId());
        verify(decisions).findByFundWithdrawalIdAndBusinessIdOrderByActedAtAsc(w.getFundWithdrawalId(),businessId);
    }
    @Test void pendingRejectedMissingOrMismatchedTransferBlocksApproval() {
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOfSatisfying(AppException.class,
            e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WITHDRAWAL_CONFIRMATION_REQUIRED));
        FundWithdrawal w=existing(FundStatus.PENDING);
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOf(AppException.class);
        w.setStatus(FundStatus.REJECTED);
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOf(AppException.class);
        w.setStatus(FundStatus.CONFIRMED); w.setAmount(BigDecimal.ONE);
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOf(AppException.class);
    }
    @Test void correctionKeepsIdAndRecordsBeforeAfterValues() {
        FundWithdrawal w=existing(FundStatus.CONFIRMED); UUID id=w.getFundWithdrawalId();
        var f=figures(); f.setWithdrawalAmount(new BigDecimal("900000")); f.setWithdrawnBy(recorder);
        assertThat(service.describeCloseCorrection(close,f)).containsKeys("before","after");
        close.setWithdrawalAmount(f.getWithdrawalAmount());
        service.syncCloseWithdrawal(close,f,"Wrong amount and person");
        assertThat(w.getFundWithdrawalId()).isEqualTo(id);
        assertThat(w.getStatus()).isEqualTo(FundStatus.PENDING);
        assertThat(w.getAmount()).isEqualByComparingTo("900000");
        var capture=org.mockito.ArgumentCaptor.forClass(FundWithdrawalDecision.class);
        verify(decisions).saveAndFlush(capture.capture());
        var decision=capture.getValue();
        assertThat(decision.getAction()).isEqualTo(FundWithdrawalAction.EDIT);
        assertThat(decision.getOldStatus()).isEqualTo(FundStatus.CONFIRMED);
        assertThat(decision.getActedBy()).isEqualTo(recorder);
        assertThat(decision.getNote()).isEqualTo("Wrong amount and person");
        assertThat(((Map<?,?>)decision.getChanges().get("before")).get("amount")).isEqualTo(new BigDecimal("1000000"));
        assertThat(((Map<?,?>)decision.getChanges().get("after")).get("amount")).isEqualTo(new BigDecimal("900000"));
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void directCorrectionOfCloseLinkedWithdrawalUpdatesCloseAmount() {
        FundWithdrawal w=existing(FundStatus.CONFIRMED);
        var request=new CorrectFundWithdrawalRequest();
        request.setAmount(new BigDecimal("900000"));
        request.setWithdrawnBy(recorder);
        request.setWithdrawnAt(time.plusSeconds(120));
        request.setEditReason("Wrong close withdrawal amount");
        request.setNote("Updated by fund correction");

        var response=service.correct(branchId,w.getFundWithdrawalId(),request);

        assertThat(close.getWithdrawalAmount()).isEqualByComparingTo("900000");
        assertThat(response.amount()).isEqualByComparingTo("900000");
        assertThat(response.status()).isEqualTo(FundStatus.PENDING);
        assertThat(response.note()).isEqualTo("Updated by fund correction");
        verify(decisions).saveAndFlush(argThat(d -> d.getAction()==FundWithdrawalAction.EDIT
                && d.getOldStatus()==FundStatus.CONFIRMED
                && d.getNewStatus()==FundStatus.PENDING));
    }
    @Test void directCorrectionOfCloseLinkedWithdrawalRequiresEditableClose() {
        FundWithdrawal w=existing(FundStatus.CONFIRMED);
        close.setStatus(CloseStatus.APPROVED);
        var request=new CorrectFundWithdrawalRequest();
        request.setAmount(new BigDecimal("900000"));
        request.setWithdrawnBy(recorder);
        request.setWithdrawnAt(time.plusSeconds(120));
        request.setEditReason("Wrong close withdrawal amount");

        assertThatThrownBy(()->service.correct(branchId,w.getFundWithdrawalId(),request))
                .isInstanceOfSatisfying(AppException.class,
                        e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOSE_FROZEN));
        assertThat(close.getWithdrawalAmount()).isEqualByComparingTo("1000000");
        verify(decisions,never()).saveAndFlush(any());
    }
    @Test void timeOnlyCorrectionRequiresFreshConfirmation() {
        FundWithdrawal w=existing(FundStatus.CONFIRMED);
        var f=CashCloseFiguresRequest.builder().withdrawnAt(time.plusSeconds(60)).build();
        assertThat(service.describeCloseCorrection(close,f)).isNotEmpty();
        service.syncCloseWithdrawal(close,f,"Wrong time");
        assertThat(w.getWithdrawnAt()).isEqualTo(time.plusSeconds(60));
        assertThat(w.getStatus()).isEqualTo(FundStatus.PENDING);
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void cancellationOfConfirmedWithdrawalAlsoNeedsConfirmation() {
        FundWithdrawal w=existing(FundStatus.CONFIRMED);
        var f=CashCloseFiguresRequest.builder().withdrawalAmount(BigDecimal.ZERO).build();
        close.setWithdrawalAmount(BigDecimal.ZERO);
        service.syncCloseWithdrawal(close,f,"No withdrawal occurred");
        assertThat(w.getAmount()).isZero();
        assertThat(w.getStatus()).isEqualTo(FundStatus.PENDING);
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOf(AppException.class);
    }
    @Test void rejectedWithdrawalNeedsCorrectionBeforeConfirmation() {
        var w=existing(FundStatus.REJECTED);
        TenantContext.set(TenantContext.of(businessId,manager));
        assertThatThrownBy(()->service.confirm(branchId,w.getFundWithdrawalId())).isInstanceOf(AppException.class);
    }
    @Test void rejectedDeclarationCanBeReissuedWithAnEditDecision() {
        var w=existing(FundStatus.REJECTED);
        service.syncCloseWithdrawal(close,figures(),"Verified declaration");
        assertThat(w.getStatus()).isEqualTo(FundStatus.PENDING);
        verify(decisions).saveAndFlush(argThat(d -> d.getAction()==FundWithdrawalAction.EDIT
            && d.getOldStatus()==FundStatus.REJECTED && d.getNote().equals("Verified declaration")));
    }
    @Test void unchangedConfirmedFiguresDoNotResetConfirmation() {
        existing(FundStatus.CONFIRMED);
        assertThat(service.describeCloseCorrection(close,figures())).isEmpty();
        service.syncCloseWithdrawal(close,figures(),"Only other close fields changed");
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void standaloneSafeTransferHasNoCloseAndAcceptsReverseDirection() {
        var request=new FundWithdrawalRequest(); request.setFromPot(CashPot.CENTRAL_SAFE); request.setToPot(CashPot.BRANCH_SAFE);
        request.setAmount(BigDecimal.TEN); request.setWithdrawnBy(manager); request.setWithdrawnAt(time);
        var response=service.record(branchId,request);
        assertThat(response.cashCloseId()).isNull(); assertThat(response.fromPot()).isEqualTo(CashPot.CENTRAL_SAFE);
        assertThat(response.status()).isEqualTo(FundStatus.PENDING);
    }
    @Test void configuredWarningUsesEnteredTransferAmountAndDoesNotBlockRecording() {
        when(config.number(branchId,"FUND_WITHDRAWAL_WARNING_ABS",BigDecimal.ZERO)).thenReturn(BigDecimal.TEN);
        var request=new FundWithdrawalRequest(); request.setFromPot(CashPot.BRANCH_SAFE); request.setToPot(CashPot.CENTRAL_SAFE);
        request.setAmount(new BigDecimal("20")); request.setWithdrawnBy(manager); request.setWithdrawnAt(time);
        var response=service.record(branchId,request);
        assertThat(response.status()).isEqualTo(FundStatus.PENDING);
        assertThat(response.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo("WITHDRAW_OVER_WARNING_THRESHOLD");
            assertThat(w.amount()).isEqualByComparingTo("20");
            assertThat(w.limit()).isEqualByComparingTo("10");
        });
    }

    @Test void crossBranchAndCrossBusinessDecisionsAreDenied() {
        var w=existing(FundStatus.PENDING); w.setBranchId(UUID.randomUUID());
        assertThatThrownBy(()->service.confirm(branchId,w.getFundWithdrawalId())).isInstanceOf(AppException.class);
        w.setBranchId(branchId); w.setBusinessId(UUID.randomUUID());
        assertThatThrownBy(()->service.confirm(branchId,w.getFundWithdrawalId())).isInstanceOf(AppException.class);
    }
}

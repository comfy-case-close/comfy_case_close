package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.*;
import com.fnbx.cashclose.entity.*;
import com.fnbx.cashclose.enums.*;
import com.fnbx.cashclose.repository.FundWithdrawalRepository;
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
    final BranchAccessGuard guard=mock(BranchAccessGuard.class);
    final EntityManager em=mock(EntityManager.class);
    final com.fnbx.cashclose.service.EffectiveConfig config=mock(com.fnbx.cashclose.service.EffectiveConfig.class);
    final FundWithdrawalServiceImpl service=new FundWithdrawalServiceImpl(repository,guard,em,config);
    final CashClose close=new CashClose();
    @BeforeEach void setup() {
        TenantContext.set(TenantContext.of(businessId,recorder));
        when(config.number(any(),anyString(),any())).thenReturn(BigDecimal.ZERO);
        close.setCashCloseId(UUID.randomUUID()); close.setBusinessId(businessId); close.setBranchId(branchId);
        close.setWithdrawalAmount(new BigDecimal("1000000"));
        when(repository.findByCashCloseIdAndStatusNot(any(),eq(FundStatus.SUPERSEDED))).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
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
        w.setFundWithdrawalId(id); w.setTransferId(id); w.setRevision(1);
        w.setBusinessId(businessId); w.setBranchId(branchId); w.setCashCloseId(close.getCashCloseId());
        w.setAmount(close.getWithdrawalAmount()); w.setWithdrawnBy(manager); w.setWithdrawnAt(time);
        w.setFromPot(CashPot.DRAWER); w.setToPot(CashPot.BRANCH_SAFE); w.setStatus(status);
        if(status==FundStatus.CONFIRMED) {w.setConfirmedBy(manager); w.setConfirmedAt(time);}
        when(repository.findByCashCloseIdAndStatusNot(close.getCashCloseId(),FundStatus.SUPERSEDED)).thenReturn(Optional.of(w));
        when(repository.findById(id)).thenReturn(Optional.of(w));
        when(em.find(FundWithdrawal.class,id)).thenReturn(w);
        when(em.find(CashClose.class,close.getCashCloseId())).thenReturn(close);
        return w;
    }
    @Test void submissionCreatesPendingTransferWithDistinctRecorderAndWithdrawer() {
        service.syncCloseWithdrawal(close,figures(),null);
        verify(repository).saveAndFlush(argThat(w->w.getStatus()==FundStatus.PENDING
            && w.getCashCloseId().equals(close.getCashCloseId()) && w.getWithdrawnBy().equals(manager)
            && w.getRecordedBy().equals(recorder) && w.getRevision()==1
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
        assertThat(service.confirm(branchId,w.getFundWithdrawalId()).confirmedBy()).isEqualTo(manager);
        service.requireConfirmed(close);
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
    @Test void correctionPreservesConfirmationAndCreatesPendingRevision() {
        FundWithdrawal old=existing(FundStatus.CONFIRMED);
        var f=figures(); f.setWithdrawalAmount(new BigDecimal("900000")); f.setWithdrawnBy(recorder);
        assertThat(service.describeCloseCorrection(close,f)).containsKeys("before","after");
        close.setWithdrawalAmount(f.getWithdrawalAmount());
        service.syncCloseWithdrawal(close,f,"Wrong amount and person");
        assertThat(old.getStatus()).isEqualTo(FundStatus.SUPERSEDED);
        assertThat(old.getConfirmedBy()).isEqualTo(manager);
        verify(repository).saveAndFlush(argThat(w->w!=old && w.getRevision()==2
            && w.getTransferId().equals(old.getTransferId()) && w.getSupersedesId().equals(old.getFundWithdrawalId())
            && w.getConfirmedAt()==null && w.getStatus()==FundStatus.PENDING
            && w.getAmount().compareTo(new BigDecimal("900000"))==0));
    }
    @Test void timeOnlyCorrectionRequiresFreshConfirmation() {
        FundWithdrawal old=existing(FundStatus.CONFIRMED);
        var f=CashCloseFiguresRequest.builder().withdrawnAt(time.plusSeconds(60)).build();
        assertThat(service.describeCloseCorrection(close,f)).isNotEmpty();
        service.syncCloseWithdrawal(close,f,"Wrong time");
        verify(repository).saveAndFlush(argThat(w->w!=old && w.getWithdrawnAt().equals(time.plusSeconds(60))));
    }
    @Test void cancellationOfConfirmedWithdrawalAlsoNeedsConfirmation() {
        FundWithdrawal old=existing(FundStatus.CONFIRMED);
        var f=CashCloseFiguresRequest.builder().withdrawalAmount(BigDecimal.ZERO).build();
        close.setWithdrawalAmount(BigDecimal.ZERO);
        service.syncCloseWithdrawal(close,f,"No withdrawal occurred");
        verify(repository).saveAndFlush(argThat(w->w!=old && w.getAmount().signum()==0 && w.getStatus()==FundStatus.PENDING));
        FundWithdrawal pending=existing(FundStatus.PENDING);
        pending.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(()->service.requireConfirmed(close)).isInstanceOf(AppException.class);
    }
    @Test void supersededRevisionCannotBeConfirmed() {
        var w=existing(FundStatus.SUPERSEDED);
        TenantContext.set(TenantContext.of(businessId,manager));
        assertThatThrownBy(()->service.confirm(branchId,w.getFundWithdrawalId())).isInstanceOf(AppException.class);
    }
    @Test void rejectedDeclarationCanBeReissuedWithoutOverwritingItsReason() {
        var old=existing(FundStatus.REJECTED); old.setRejectedBy(manager); old.setRejectedAt(time); old.setRejectionReason("Check again");
        service.syncCloseWithdrawal(close,figures(),"Verified declaration");
        assertThat(old.getRejectionReason()).isEqualTo("Check again");
        assertThat(old.getStatus()).isEqualTo(FundStatus.SUPERSEDED);
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

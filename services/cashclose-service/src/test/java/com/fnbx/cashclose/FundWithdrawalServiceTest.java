package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.FundWithdrawalRequest;
import com.fnbx.cashclose.entity.FundWithdrawal;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.FundWithdrawalRepository;
import com.fnbx.cashclose.service.impl.FundWithdrawalServiceImpl;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FundWithdrawalServiceTest {
    private final UUID businessId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final FundWithdrawalRepository withdrawals = mock(FundWithdrawalRepository.class);
    private final CashCloseRepository closes = mock(CashCloseRepository.class);
    private final CashCloseCalcRepository calculations = mock(CashCloseCalcRepository.class);
    private final BranchAccessGuard guard = mock(BranchAccessGuard.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final FundWithdrawalServiceImpl service = new FundWithdrawalServiceImpl(
            withdrawals, closes, calculations, guard, entityManager);

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void calculatesExpectedAmountFromApprovedClosesAndKeepsCountedAmountSeparate() {
        TenantContext.set(TenantContext.of(businessId, staffId));
        Branch branch = new Branch();
        branch.setBranchId(branchId);
        branch.setBusinessId(businessId);
        branch.setBranchCode("BR01");
        branch.setBranchName("Branch One");
        when(entityManager.find(Branch.class, branchId)).thenReturn(branch);
        Business business = new Business();
        business.setBusinessId(businessId);
        business.setTimezone("Asia/Ho_Chi_Minh");
        when(entityManager.find(Business.class, businessId)).thenReturn(business);
        when(withdrawals.findLiveOverlapping(eq(branchId), any(), any())).thenReturn(List.of());
        when(withdrawals.sumApprovedWithdrawals(eq(branchId), any(), any()))
                .thenReturn(new BigDecimal("100"));
        when(withdrawals.sumLiveThrough(eq(branchId), any())).thenReturn(BigDecimal.ZERO);
        when(closes.findByBranchIdAndBusinessDateBetweenAndStatusOrderByBusinessDateDesc(
                eq(branchId), any(), any(), any())).thenReturn(List.of());
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("branchId"), eq(branchId))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new BigDecimal("50"));

        FundWithdrawalRequest request = new FundWithdrawalRequest();
        request.setFromDate(LocalDate.now().minusDays(1));
        request.setToDate(LocalDate.now().minusDays(1));
        request.setActualReceivedAmount(new BigDecimal("80"));
        var response = service.record(branchId, request);

        assertThat(response.warnings()).extracting(w -> w.code())
                .containsExactly("WITHDRAW_OVER_WARNING_THRESHOLD");
        verify(withdrawals).saveAndFlush(argThat(w -> w.getBusinessId().equals(businessId)
                && w.getBranchId().equals(branchId) && w.getCreatedBy().equals(staffId)
                && w.getSystemWithdrawAmount().compareTo(new BigDecimal("100")) == 0
                && w.getActualReceivedAmount().compareTo(new BigDecimal("80")) == 0
                && w.getSystemPotAfter().compareTo(BigDecimal.ZERO) == 0));
        verify(withdrawals).sumApprovedWithdrawals(branchId, request.getFromDate(), request.getToDate());
        verify(guard).require(branchId, Permission.WITHDRAWAL_RECORD);
    }

    @Test
    void refusesToRecordWithdrawalWithoutApprovedSourceCloses() {
        TenantContext.set(TenantContext.of(businessId, staffId));
        Branch branch = new Branch();
        branch.setBranchId(branchId);
        branch.setBusinessId(businessId);
        when(entityManager.find(Branch.class, branchId)).thenReturn(branch);
        Business business = new Business();
        business.setBusinessId(businessId);
        business.setTimezone("Asia/Ho_Chi_Minh");
        when(entityManager.find(Business.class, businessId)).thenReturn(business);
        when(withdrawals.findLiveOverlapping(eq(branchId), any(), any())).thenReturn(List.of());
        when(withdrawals.sumApprovedWithdrawals(eq(branchId), any(), any())).thenReturn(BigDecimal.ZERO);
        when(withdrawals.sumLiveThrough(eq(branchId), any())).thenReturn(BigDecimal.ZERO);
        FundWithdrawalRequest request = new FundWithdrawalRequest();
        request.setActualReceivedAmount(new BigDecimal("80"));

        assertThatThrownBy(() -> service.record(branchId, request))
                .hasMessageContaining("No approved cash-close withdrawals");
        verify(withdrawals, never()).saveAndFlush(any(FundWithdrawal.class));
    }
}

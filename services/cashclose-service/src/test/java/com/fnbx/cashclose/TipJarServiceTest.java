package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.TipPayoutRequest;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.repository.TipPayoutRepository;
import com.fnbx.cashclose.service.impl.TipJarServiceImpl;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Business;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TipJarServiceTest {
    private final UUID businessId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final TipPayoutRepository payouts = mock(TipPayoutRepository.class);
    private final CashMovementRepository movements = mock(CashMovementRepository.class);
    private final BranchAccessGuard guard = mock(BranchAccessGuard.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final TipJarServiceImpl service = new TipJarServiceImpl(payouts, movements, guard, entityManager);

    @BeforeEach
    void setUp() {
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
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    @Test
    void jarBalanceUsesPooledTipsAndExcludesTipsHandedDirectlyToStaff() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate from = today.withDayOfMonth(1);
        when(payouts.findByBranchIdAndPayoutDateBetweenOrderByPayoutDateDescCreatedAtDesc(branchId, from, today))
                .thenReturn(List.of());
        when(payouts.sumInRange(eq(branchId), any(), any())).thenReturn(new BigDecimal("20"));
        when(movements.sumTipsByKind(eq(branchId), any(), any(), eq("TIP_JAR")))
                .thenReturn(new BigDecimal("100"));
        when(movements.sumTipsByKind(eq(branchId), any(), any(), eq("TIP_IN_DRAWER")))
                .thenReturn(new BigDecimal("30"));

        var response = service.getTipJar(branchId, from, today);

        assertThat(response.summary().tipsIn()).isEqualByComparingTo("100");
        assertThat(response.summary().tipsInsideDrawer()).isEqualByComparingTo("30");
        assertThat(response.summary().balance()).isEqualByComparingTo("80");
        verify(movements, never()).sumTipsByKind(eq(branchId), any(), any(), eq("TIP_DIRECT"));
        verify(guard).require(branchId, Permission.FINANCE_READ);
    }

    @Test
    void payoutIsScopedToCurrentTenantAndWarnsWhenItExceedsTheJar() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(movements.sumTipsByKind(eq(branchId), any(), eq(today), eq("TIP_JAR")))
                .thenReturn(new BigDecimal("100"));
        when(payouts.sumInRange(eq(branchId), any(), eq(today))).thenReturn(BigDecimal.ZERO);
        TipPayoutRequest request = new TipPayoutRequest();
        request.setAmount(new BigDecimal("120"));

        var result = service.recordPayout(branchId, request);

        assertThat(result.balanceAfter()).isEqualByComparingTo("-20");
        assertThat(result.warnings()).extracting(w -> w.code())
                .containsExactly("PAYOUT_EXCEEDS_JAR_BALANCE");
        assertThat(result.payout().branchId()).isEqualTo(branchId);
        assertThat(result.payout().createdById()).isEqualTo(staffId);
        verify(payouts).saveAndFlush(argThat(p -> p.getBusinessId().equals(businessId)
                && p.getBranchId().equals(branchId) && p.getAmount().compareTo(new BigDecimal("120")) == 0));
        verify(guard).require(branchId, Permission.WITHDRAWAL_RECORD);
    }
}

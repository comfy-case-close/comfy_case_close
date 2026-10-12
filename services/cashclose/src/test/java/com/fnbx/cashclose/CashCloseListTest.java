package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.CashCloseListFilter;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseDecisionRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.identity.entity.Business;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashCloseListTest {
    @Mock CashCloseRepository closeRepository;
    @Mock CashCloseCalcRepository calcRepository;
    @Mock CashCloseDecisionRepository closeDecisionRepository;
    @Mock BranchAccessGuard branchAccess;
    @Mock EntityManager entityManager;
    @Spy CashCloseMapper mapper = Mappers.getMapper(CashCloseMapper.class);
    @InjectMocks CashCloseServiceImpl service;
    UUID businessId = UUID.randomUUID(), branchId = UUID.randomUUID();

    @BeforeEach void context() {
        TenantContext.set(TenantContext.of(businessId, UUID.randomUUID()));
        Business business = new Business();
        business.setTimezone("Asia/Ho_Chi_Minh");
        when(entityManager.find(Business.class, businessId)).thenReturn(business);
        when(branchAccess.branches(Permission.CLOSE_READ)).thenReturn(Set.of(branchId));
    }

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void emptyPageSkipsRelatedQueries() {
        var pageable = PageRequest.of(0, 100);
        when(closeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        var result = service.listCashCloses(branchId, new CashCloseListFilter(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getSize()).isEqualTo(100);
        verifyNoInteractions(calcRepository, closeDecisionRepository);
        verify(entityManager, never()).flush();
    }

    @Test void missingCalculationKeepsZeroTotalsAndPageOrder() {
        CashClose first = new CashClose(), second = new CashClose();
        first.setCashCloseId(UUID.randomUUID());
        second.setCashCloseId(UUID.randomUUID());
        CashCloseCalc calc = mock(CashCloseCalc.class);
        when(calc.getCashCloseId()).thenReturn(second.getCashCloseId());
        when(calc.getCountedCash()).thenReturn(new BigDecimal("500000"));
        var pageable = PageRequest.of(0, 100);
        when(closeRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(calcRepository.findAllById(List.of(first.getCashCloseId(), second.getCashCloseId())))
                .thenReturn(List.of(calc));

        var result = service.listCashCloses(branchId, new CashCloseListFilter(), pageable);

        assertThat(result.getContent()).extracting("cashCloseId")
                .containsExactly(first.getCashCloseId(), second.getCashCloseId());
        assertThat(result.getContent().getFirst().getCountedCash()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getContent().getFirst().getRiskLevel()).isNull();
        assertThat(result.getContent().get(1).getCountedCash()).isEqualByComparingTo("500000");
        verify(calcRepository).findAllById(List.of(first.getCashCloseId(), second.getCashCloseId()));
        verifyNoMoreInteractions(calcRepository);
        verifyNoInteractions(closeDecisionRepository);
        verify(entityManager, never()).flush();
        verify(entityManager, never()).refresh(any());
    }
}

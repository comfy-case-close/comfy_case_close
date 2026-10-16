package com.fnbx.platform;

import com.fnbx.platform.entity.MovementKind;
import com.fnbx.platform.repository.MovementKindRepository;
import com.fnbx.platform.service.MovementKindService;
import com.fnbx.shared.enums.EffectType;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovementKindServiceTest {
    private final MovementKindRepository kinds = mock(MovementKindRepository.class);
    private final BranchAccessGuard access = mock(BranchAccessGuard.class);
    private final MovementKindService service = new MovementKindService(kinds, access);
    private final UUID business = UUID.randomUUID();

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void mapsTenantCatalogueAndPageMetadata() {
        TenantContext.set(TenantContext.of(business, UUID.randomUUID()));
        MovementKind kind = new MovementKind();
        ReflectionTestUtils.setField(kind, "kindSk", 42L);
        kind.setBusinessId(business);
        kind.setKindCode("LOCAL_EXPENSE");
        kind.setDisplayName("Local expense");
        kind.setEffectType(EffectType.CASH_OUT);
        kind.setValidFrom(LocalDate.of(2026, 1, 1));
        kind.setValidTo(LocalDate.of(2027, 1, 1));
        kind.setAffectsDifference(true);
        kind.setAffectsRemaining(true);
        kind.setRequiresNote(true);
        kind.setRequiresReceipt(true);
        kind.setExpenseCategory("SUPPLY");
        kind.setDiffReasonGroup("NOT_IN_POS");
        var pageable = PageRequest.of(1, 2);
        when(kinds.findAllVisible(business, pageable)).thenReturn(new PageImpl<>(List.of(kind), pageable, 3));

        var response = service.list(pageable);

        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isLast()).isTrue();
        assertThat(response.getContent()).singleElement().satisfies(row -> {
            assertThat(row.kindSk()).isEqualTo(42L);
            assertThat(row.kindCode()).isEqualTo("LOCAL_EXPENSE");
            assertThat(row.businessId()).isEqualTo(business);
            assertThat(row.validFrom()).isEqualTo(kind.getValidFrom());
            assertThat(row.validTo()).isEqualTo(kind.getValidTo());
            assertThat(row.displayName()).isEqualTo("Local expense");
            assertThat(row.effectType()).isEqualTo(EffectType.CASH_OUT);
            assertThat(row.affectsDifference()).isTrue();
            assertThat(row.affectsRemaining()).isTrue();
            assertThat(row.requiresReceipt()).isTrue();
            assertThat(row.requiresNote()).isTrue();
            assertThat(row.expenseCategory()).isEqualTo("SUPPLY");
            assertThat(row.diffReasonGroup()).isEqualTo("NOT_IN_POS");
        });
        var order = inOrder(access, kinds);
        order.verify(access).requireActiveStaff();
        order.verify(kinds).findAllVisible(business, pageable);
    }

    @Test void inactiveMembershipCannotReadCatalogue() {
        TenantContext.set(TenantContext.of(business, UUID.randomUUID()));
        doThrow(new AccessDeniedException("Inactive staff")).when(access).requireActiveStaff();
        assertThatThrownBy(() -> service.list(PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(kinds);
    }

    @Test void emptyCatalogueKeepsEmptyPageMetadata() {
        TenantContext.set(TenantContext.of(business, UUID.randomUUID()));
        var pageable = PageRequest.of(0, 20);
        when(kinds.findAllVisible(business, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        var response = service.list(pageable);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isLast()).isTrue();
    }
}

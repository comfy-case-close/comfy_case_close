package com.fnbx.hrm.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.dto.request.PayrollConfigRequest;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.enums.AppliesTo;
import com.fnbx.hrm.enums.PeriodMode;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Versions of the payroll parameters are append-only: a new one starts after the newest, and carries the birthday allowance. */
class PayrollConfigVersioningTest {

    private final PayrollConfigRepository repository = mock(PayrollConfigRepository.class);
    private final PayrollConfigServiceImpl service = new PayrollConfigServiceImpl(
            repository, mock(PayrollSettingMapper.class), mock(BranchAccessGuard.class), mock(PayrollAccess.class));

    @BeforeEach
    void signIn() {
        TenantContext.set(TenantContext.of(UUID.randomUUID(), UUID.randomUUID()));
    }

    @AfterEach
    void signOut() {
        TenantContext.clear();
    }

    private static PayrollConfig existing(LocalDate from) {
        PayrollConfig config = new PayrollConfig();
        config.setEffectiveFrom(from);
        return config;
    }

    private static PayrollConfigRequest request(LocalDate from) {
        PayrollConfigRequest request = new PayrollConfigRequest();
        request.setEffectiveFrom(from);
        request.setStandardDaysPerMonth(new BigDecimal("26"));
        request.setStandardHoursPerDay(new BigDecimal("8"));
        request.setOvertimeMultiplier(new BigDecimal("1.5"));
        request.setWeekendMultiplier(new BigDecimal("2"));
        request.setPeriodMode(PeriodMode.CURRENT_MONTH);
        return request;
    }

    @Test
    void refusesAVersionDatedBeforeOrOnTheNewestOne() {
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(existing(LocalDate.of(2099, 6, 1))));

        assertThrows(AppException.class, () -> service.create(request(LocalDate.of(2099, 3, 1))));
        assertThrows(AppException.class, () -> service.create(request(LocalDate.of(2099, 6, 1))));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsALaterVersionAndStoresTheBirthdayAllowance() {
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(existing(LocalDate.of(2026, 1, 1))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        PayrollConfigRequest request = request(LocalDate.of(2027, 1, 1));
        request.setBirthdayAllowanceAmount(new BigDecimal("200000"));
        request.setBirthdayAllowanceAppliesTo(AppliesTo.FULLTIME);

        service.create(request);

        ArgumentCaptor<PayrollConfig> saved = ArgumentCaptor.forClass(PayrollConfig.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(new BigDecimal("200000"), saved.getValue().getBirthdayAllowanceAmount());
        assertEquals(AppliesTo.FULLTIME, saved.getValue().getBirthdayAllowanceAppliesTo());
    }

    @Test
    void theFirstVersionIsAlwaysAccepted() {
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.create(request(LocalDate.of(2026, 1, 1)));

        verify(repository).saveAndFlush(any());
    }
}

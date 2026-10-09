package com.fnbx.hrm.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.dto.request.LatePenaltyRuleRequest;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.LatePenaltyRuleRepository;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A new version of a rule takes over from its start date, so two versions of one code are never in force together. */
class LatePenaltyRuleVersioningTest {

    private final LatePenaltyRuleRepository repository = mock(LatePenaltyRuleRepository.class);
    private final LatePenaltyRuleServiceImpl service = new LatePenaltyRuleServiceImpl(
            repository, mock(PayrollSettingMapper.class), mock(BranchAccessGuard.class), mock(PayrollAccess.class));

    @BeforeEach
    void signIn() {
        TenantContext.set(TenantContext.of(UUID.randomUUID(), UUID.randomUUID()));
    }

    @AfterEach
    void signOut() {
        TenantContext.clear();
    }

    private static LatePenaltyRule rule(String code, LocalDate from, LocalDate to) {
        LatePenaltyRule rule = new LatePenaltyRule();
        rule.setRuleCode(code);
        rule.setEffectiveFrom(from);
        rule.setEffectiveTo(to);
        return rule;
    }

    private static LatePenaltyRuleRequest request(String code, LocalDate from) {
        LatePenaltyRuleRequest request = new LatePenaltyRuleRequest();
        request.setRuleCode(code);
        request.setEffectiveFrom(from);
        return request;
    }

    @Test
    void endsTheOpenVersionTheDayBeforeTheNewOneStarts() {
        LatePenaltyRule open = rule("T2", LocalDate.of(2026, 1, 1), null);
        LatePenaltyRule otherCode = rule("T1", LocalDate.of(2026, 1, 1), null);
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(open, otherCode));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.create(request("T2", LocalDate.of(2027, 3, 1)));

        assertEquals(LocalDate.of(2027, 2, 28), open.getEffectiveTo());
        assertNull(otherCode.getEffectiveTo());
    }

    @Test
    void leavesAVersionThatAlreadyEndedBeforeTheNewOneAlone() {
        LatePenaltyRule closed = rule("T2", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        when(repository.findAllByOrderByEffectiveFromDesc()).thenReturn(List.of(closed));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.create(request("T2", LocalDate.of(2026, 1, 1)));

        assertEquals(LocalDate.of(2025, 12, 31), closed.getEffectiveTo());
    }

    @Test
    void refusesAVersionThatStartsOnOrBeforeTheNewestOne() {
        when(repository.findAllByOrderByEffectiveFromDesc())
                .thenReturn(List.of(rule("T2", LocalDate.of(2026, 6, 1), null)));

        assertThrows(AppException.class, () -> service.create(request("T2", LocalDate.of(2026, 6, 1))));
        verify(repository, never()).save(any());
    }
}

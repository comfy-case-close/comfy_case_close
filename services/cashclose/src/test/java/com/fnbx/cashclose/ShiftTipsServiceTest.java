package com.fnbx.cashclose;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.service.impl.ShiftTipsServiceImpl;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShiftTipsServiceTest {
    @Test
    void showsEveryDeclaredTipButOnlyApprovedTipsAsPayable() {
        UUID branchId = UUID.randomUUID();
        UUID shiftTypeId = UUID.randomUUID();
        UUID closeId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 27);
        CashClose close = new CashClose();
        close.setCashCloseId(closeId);
        close.setStatus(CloseStatus.SUBMITTED);
        CashMovement approved = tip(closeId, "30000");
        approved.setApprovalStatus(com.fnbx.cashclose.enums.MovementStatus.APPROVED);
        CashMovement pending = tip(closeId, "20000");
        CashMovement rejected = tip(closeId, "10000");
        rejected.setApprovalStatus(com.fnbx.cashclose.enums.MovementStatus.REJECTED);

        CashCloseRepository closes = mock(CashCloseRepository.class);
        CashMovementRepository movements = mock(CashMovementRepository.class);
        BranchAccessGuard guard = mock(BranchAccessGuard.class);
        CashCloseMapper mapper = mock(CashCloseMapper.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(closes.findByBranchIdAndShiftTypeIdAndBusinessDateAndStatusNot(
                branchId, shiftTypeId, date, CloseStatus.VOIDED)).thenReturn(Optional.of(close));
        when(movements.findShiftTips(closeId)).thenReturn(List.of(approved, pending, rejected));

        var response = new ShiftTipsServiceImpl(closes, movements, guard, mapper, entityManager)
                .getShiftTips(branchId, shiftTypeId, date);

        assertThat(response.totalTips()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(response.pendingTips()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(response.pendingCount()).isEqualTo(1);
        assertThat(response.movements()).hasSize(3);
        verify(guard).require(branchId, Permission.CLOSE_READ);
    }

    private CashMovement tip(UUID closeId, String amount) {
        CashMovement movement = new CashMovement();
        movement.setCashCloseId(closeId);
        movement.setSignedAmount(new BigDecimal(amount));
        return movement;
    }
}

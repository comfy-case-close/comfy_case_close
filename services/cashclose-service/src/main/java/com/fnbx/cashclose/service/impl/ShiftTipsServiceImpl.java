package com.fnbx.cashclose.service.impl;

import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.dto.response.ShiftTipsResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.service.ShiftTipsService;
import com.fnbx.platform.entity.MovementKind;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftTipsServiceImpl implements ShiftTipsService {
    private final CashCloseRepository closes;
    private final CashMovementRepository movements;
    private final BranchAccessGuard branchAccess;
    private final CashCloseMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ShiftTipsResponse getShiftTips(UUID branchId, UUID shiftTypeId, LocalDate businessDate) {
        branchAccess.require(branchId, Permission.CLOSE_READ);
        CashClose close = closes.findByBranchIdAndShiftTypeIdAndBusinessDateAndStatusNot(
                        branchId, shiftTypeId, businessDate, CloseStatus.VOIDED)
                .orElseThrow(CashCloseExceptions::cashCloseNotFound);
        var tips = movements.findShiftTips(close.getCashCloseId());
        BigDecimal totalTips = BigDecimal.ZERO;
        BigDecimal pendingTips = BigDecimal.ZERO;
        int pending = 0;
        for (var tip : tips) {
            if (tip.getApprovalStatus() == MovementStatus.REJECTED) continue;
            BigDecimal amount = tip.getSignedAmount().abs();
            if (tip.getApprovalStatus() == MovementStatus.APPROVED) totalTips = totalTips.add(amount);
            if (tip.getApprovalStatus() == MovementStatus.PENDING) {
                pendingTips = pendingTips.add(amount);
                pending++;
            }
        }
        List<CashMovementResponse> lines = tips.stream()
                .map(tip -> mapper.toResponse(tip, entityManager.find(MovementKind.class, tip.getKindSk())))
                .toList();
        return new ShiftTipsResponse(close.getCashCloseId(), branchId, shiftTypeId,
                businessDate, close.getStatus().name(), totalTips, pendingTips, pending, lines);
    }
}

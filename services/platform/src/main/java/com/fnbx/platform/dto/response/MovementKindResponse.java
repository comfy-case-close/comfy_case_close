package com.fnbx.platform.dto.response;

import com.fnbx.shared.enums.EffectType;

import java.time.LocalDate;
import java.util.UUID;

public record MovementKindResponse(
        Long kindSk,
        String kindCode,
        UUID businessId,
        LocalDate validFrom,
        LocalDate validTo,
        EffectType effectType,
        boolean affectsDifference,
        boolean affectsRemaining,
        String expenseCategory,
        String diffReasonGroup,
        boolean requiresReceipt,
        boolean requiresNote,
        String displayName) {
}

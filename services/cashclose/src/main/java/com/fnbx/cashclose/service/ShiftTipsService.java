package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.response.ShiftTipsResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface ShiftTipsService {
    ShiftTipsResponse getShiftTips(UUID branchId, UUID shiftTypeId, LocalDate businessDate);
}

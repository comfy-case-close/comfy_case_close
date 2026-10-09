package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.PaymentStatus;
import java.util.UUID;

public record PaymentFilter(UUID periodId, PaymentStatus status, String bankCode, UUID branchId, String search) {
}

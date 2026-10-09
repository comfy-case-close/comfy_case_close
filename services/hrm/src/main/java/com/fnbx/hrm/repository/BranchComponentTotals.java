package com.fnbx.hrm.repository;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchComponentTotals(UUID branchId, String employmentType, String componentCode, BigDecimal amount) {
}

package com.fnbx.hrm.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** One branch and employment type with the pay figures summed over some set of payroll lines. */
public record BranchCostTotals(UUID branchId, String employmentType, long lineCount, BigDecimal gross,
                               BigDecimal employerInsurance, BigDecimal laborCost) {
}

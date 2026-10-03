package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code branchId} null means company-wide. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchRevenueEntryRequest {
    private UUID branchId;
    @NotNull
    private BigDecimal amount;
}

package com.fnbx.identity.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A branch as the API exposes it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchResponse {

    private UUID branchId;
    private UUID businessId;
    private String branchCode;
    private String branchName;
    private String address;
    private BigDecimal targetCashRemaining;
    private BigDecimal cashRemainingTolerance;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}

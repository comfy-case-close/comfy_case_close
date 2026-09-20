package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Opens a new close for one branch, shift and business date.
 *
 * <p>There is deliberately no {@code businessId} field. It comes from the verified
 * JWT via {@code VerifiedTenantFilter}. Accepting it here would let a caller choose which
 * tenant they are.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenDraftRequest {

    @NotNull(message = "branchId is required")
    private UUID branchId;

    @NotNull(message = "shiftTypeId is required")
    private UUID shiftTypeId;

    @NotNull(message = "businessDate is required")
    private LocalDate businessDate;
}

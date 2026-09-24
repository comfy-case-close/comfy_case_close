package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Opens a new close for one shift and business date.
 *
 * <p>There is deliberately no {@code businessId} field. It comes from the verified
 * JWT via {@code VerifiedTenantFilter}. Accepting it here would let a caller choose
 * which tenant they are.
 *
 * <p>There is no longer a {@code branchId} field either, for a weaker but real
 * version of the same reason. The branch now arrives in the {@code X-Branch-Id}
 * header and is verified against the caller's live assignment in
 * {@code identity.staff_branch_role}. Keeping it in the body made it one field
 * among several that each endpoint had to remember to re-check; as a header it is
 * the scope of the whole request, checked in one place.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenDraftRequest {

    @NotNull(message = "shiftTypeId is required")
    private UUID shiftTypeId;

    @NotNull(message = "businessDate is required")
    private LocalDate businessDate;
}

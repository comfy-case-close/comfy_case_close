package com.fnbx.identity.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/**
 * Opens another branch in the caller's business. ADMIN only.
 *
 * <p>No {@code businessId}: it comes from the verified JWT. Accepting one here
 * would let a caller choose which tenant they are.
 */
public record CreateBranchRequest(
        @NotBlank @Size(max = 200) String branchName,
        @Size(max = 500) String address,

        /** Cash the owner wants left in the drawer after each shift. */
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal targetCashRemaining,

        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal cashRemainingTolerance) {}

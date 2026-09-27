package com.fnbx.identity.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/**
 * Partial update of one branch. Every field is optional; a null field is left alone.
 *
 * <p>{@code branchCode} is absent on purpose: it is part of the generated cash-close
 * code, so renaming it would make older closes unreadable. Deactivation is
 * {@code DELETE /branches/{id}}, not a flag here.
 */
public record UpdateBranchRequest(
        @Size(min = 1, max = 200) String branchName,
        @Size(max = 500) String address,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal targetCashRemaining,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal cashRemainingTolerance) {}

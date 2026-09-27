package com.fnbx.identity.dto.request;

import com.fnbx.shared.enums.BusinessType;
import jakarta.validation.constraints.*;

/**
 * Partial update of the caller's own business. Every field is optional; a null
 * field is left alone.
 *
 * <p>There is no {@code businessCode} and no {@code isActive}. The code is the
 * tenant's public handle and is immutable; deactivation is a platform act, because
 * an ADMIN switching off their own business would lock out every colleague with no
 * way back in from inside the product.
 */
public record UpdateBusinessRequest(
        @Size(min = 1, max = 200) String businessName,
        BusinessType businessType,
        @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO currency code")
        String currencyCode,
        @Size(min = 1, max = 64) String timezone) {}

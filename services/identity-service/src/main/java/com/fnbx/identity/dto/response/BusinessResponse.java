package com.fnbx.identity.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.shared.enums.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A business as the API exposes it.
 *
 * <p>{@code firstBranchId} is populated only by registration, where the branch was
 * created in the same call and the platform administrator needs it to provision the
 * owner. Reads leave it null; use {@code GET /branches} for the branch list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessResponse {

    private UUID businessId;
    private String businessCode;
    private String businessName;
    private BusinessType businessType;
    private String currencyCode;
    private String timezone;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID firstBranchId;
}

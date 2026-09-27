package com.fnbx.identity.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Identity's own row projection for {@code identity.branch}. See {@link BusinessProfile}. */
public record BranchProfile(UUID branchId, UUID businessId, String branchCode, String branchName, String address,
                            BigDecimal targetCashRemaining, BigDecimal cashRemainingTolerance, boolean active,
                            Instant createdAt, Instant updatedAt) {}

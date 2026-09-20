package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.shared.enums.BusinessType;

/**
 * Identity's own row projection for {@code identity.business}.
 *
 * <p>A record rather than the JPA {@code Business} entity from
 * {@code fnbx-entities-identity}: identity reads through JDBC and never starts
 * Hibernate, so a mutable managed entity here would be a detached object
 * pretending to be attached. The two also share the package
 * {@code com.fnbx.identity.entity} across jars, which is why this is not simply
 * called {@code Business}.
 */
public record BusinessProfile(UUID businessId, String businessCode, String businessName, BusinessType businessType,
                              String currencyCode, String timezone, boolean active,
                              Instant createdAt, Instant updatedAt) {}

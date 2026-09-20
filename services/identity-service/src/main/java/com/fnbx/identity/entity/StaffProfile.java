package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity's directory projection for {@code identity.staff} - who someone is,
 * with no credential on it.
 *
 * <p>Deliberately not {@link AuthAccount}, which carries the password hash and
 * exists only to be checked against. Provisioning and assignment never need the
 * hash, so they never load it.
 */
public record StaffProfile(UUID staffId, UUID businessId, String employeeCode, String firstName, String lastName,
                           String email, String phone, String avatarUrl, boolean active, boolean emailVerified,
                           String authProvider, Instant createdAt) {}

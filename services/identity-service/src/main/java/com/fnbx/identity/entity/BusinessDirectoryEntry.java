package com.fnbx.identity.entity;

import java.util.UUID;

/**
 * What the public code lookup returns, and the reason it is its own type: a
 * {@link BusinessProfile} carries the whole row, and the lookup crosses tenant
 * isolation to answer. Narrowing the shape at the repository is what stops a later
 * edit from widening the response by accident.
 */
public record BusinessDirectoryEntry(UUID businessId, String businessName) {}

package com.fnbx.shared.enums;

/**
 * Matches PostgreSQL enum {@code shared.user_role}.
 *
 * <p>A role decides which buttons someone can press. It is separate from
 * {@code staff_position}, which is the job title printed on the badge - a shift
 * lead at one branch may only be STAFF at another.
 *
 * <p>Lives in {@code fnbx-shared} even though only {@code identity} persists it,
 * because {@code TenantContext} carries it on every request in every service.
 * Contrast with {@code CloseStatus} or {@code FundStatus}, which never leave their
 * own module and therefore live beside their entities.
 *
 * <p><b>ADMIN and HR carry business-wide authority</b>, unlike every other value
 * here. Holding either at any active branch lets the holder review join requests
 * and assign people to branches across the whole business - see
 * {@link com.fnbx.shared.security.AccessPrincipal#requireAnyBranch} and
 * {@code docs/security/onboarding.md}. HR stops short of ADMIN: it may not grant
 * ADMIN, or any HR could promote a colleague and be promoted back.
 *
 * <p>Declared in the same order as the PostgreSQL enum so {@code ORDER BY role}
 * reads as a seniority ladder in both places. Ordinals are never persisted -
 * {@code @Enumerated(STRING)} and {@code UserRole.valueOf} - so the order is
 * presentation only and reordering breaks nothing stored.
 */
public enum UserRole { ADMIN, HR, MANAGER, ACCOUNTANT, SHIFT_LEAD, STAFF }

package com.fnbx.identity.enums;

/**
 * Matches PostgreSQL enum {@code shared.registration_status}.
 *
 * <p>The lifecycle of an application for a tenant. Like
 * {@link JoinRequestStatus} there is no CANCELLED: a decision is final in both
 * directions, and a rejected applicant reapplies rather than reopening.
 */
public enum RegistrationStatus { PENDING, APPROVED, REJECTED }

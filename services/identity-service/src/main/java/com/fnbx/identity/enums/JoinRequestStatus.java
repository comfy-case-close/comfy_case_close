package com.fnbx.identity.enums;

/**
 * Matches PostgreSQL enum {@code shared.join_request_status}.
 *
 * <p>Lives beside identity rather than in {@code fnbx-shared} because no other
 * service ever sees a join request - the same reason {@code CloseStatus} lives in
 * cashclose. Contrast with {@link com.fnbx.shared.enums.UserRole}, which every
 * service reads off the token.
 *
 * <p>There is no CANCELLED. A pending request is replaced when the same address
 * submits again, and deciding one is final in both directions - a rejected
 * applicant reapplies rather than reopening.
 */
public enum JoinRequestStatus { PENDING, APPROVED, REJECTED }

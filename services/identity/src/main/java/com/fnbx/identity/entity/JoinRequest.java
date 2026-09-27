package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.identity.enums.JoinRequestStatus;

/**
 * Identity's row projection for {@code identity.staff_join_request} - somebody
 * asking to join a business that already exists.
 *
 * <p>{@code passcodeHash} is the BCrypt hash of the password the applicant chose
 * during signup, carried so approval can create a usable account without a second
 * setup email. It is null once the request is decided, and null from the start for
 * a Google-originated request. {@code toString} is overridden for the same reason
 * {@link AuthAccount} overrides it: this object must never end up in a log line.
 */
public record JoinRequest(UUID joinRequestId, UUID businessId, String email, String firstName, String lastName,
                          String phone, String passcodeHash, String authProvider, String avatarUrl,
                          JoinRequestStatus status, Instant requestedAt, UUID decidedBy, Instant decidedAt,
                          String decisionNote, UUID createdStaffId) {
    @Override public String toString() { return "JoinRequest[" + joinRequestId + "]"; }
}

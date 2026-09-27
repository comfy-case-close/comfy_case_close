package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.identity.enums.RegistrationStatus;
import com.fnbx.shared.enums.BusinessType;

/**
 * Identity's row projection for {@code identity.business_registration} - somebody
 * applying for a tenant that does not exist yet.
 *
 * <p>The one row in this service that belongs to no business, which is why the
 * table carries no {@code business_id} and takes no RLS policy. Everything here is
 * unreviewed input from the public internet until a platform administrator says
 * otherwise; treat it as data, never as something to act on unread.
 *
 * <p>{@code notifiedAt} is when the decision letter actually went out. Null on a
 * decided row means the applicant was never told.
 */
public record BusinessRegistration(UUID registrationId,
                                   String businessCode, String businessName, BusinessType businessType,
                                   String currencyCode, String timezone,
                                   String branchCode, String branchName, String branchAddress,
                                   String ownerEmail, String ownerFirstName, String ownerLastName,
                                   String ownerPhone,
                                   RegistrationStatus status, Instant submittedAt, Instant decidedAt,
                                   String decisionNote, UUID createdBusinessId, UUID createdStaffId,
                                   Instant notifiedAt, Instant ownerEmailVerifiedAt) {

    public String ownerName() { return (ownerFirstName + " " + ownerLastName).trim(); }
}

package com.fnbx.identity.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.identity.enums.RegistrationStatus;
import com.fnbx.shared.enums.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A registration as a platform administrator sees it - the whole application, plus
 * what became of it.
 *
 * <p>Only ever returned behind the platform key. The applicant never receives this
 * shape: they get an acknowledgement on submit and a letter on decision, because
 * everything here beyond their own submission is review state.
 *
 * <p>{@code notifiedAt} null on a decided row is the signal worth watching - the
 * decision was recorded but the letter never reached the applicant.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessRegistrationResponse {

    private UUID registrationId;

    private String businessCode;
    private String businessName;
    private BusinessType businessType;
    private String currencyCode;
    private String timezone;

    private String branchCode;
    private String branchName;
    private String branchAddress;

    private String ownerEmail;
    private String ownerFirstName;
    private String ownerLastName;
    private String ownerPhone;

    private RegistrationStatus status;
    private Instant submittedAt;
    private Instant decidedAt;
    private String decisionNote;

    /** Set on approval: what this registration actually produced. */
    private UUID createdBusinessId;
    private UUID createdStaffId;

    /** When the decision letter went out. Null on a decided row means it did not. */
    private Instant notifiedAt;
    private Instant ownerEmailVerifiedAt;
}

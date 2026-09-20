package com.fnbx.identity.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.fnbx.identity.enums.JoinRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A join request as a reviewer sees it.
 *
 * <p>No password hash, and no field that could carry one. The mapper reads from
 * {@link com.fnbx.identity.entity.JoinRequest}, which does hold the hash until the
 * request is decided; keeping the two shapes separate is what stops it reaching a
 * response body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinRequestResponse {

    private UUID joinRequestId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String authProvider;
    private JoinRequestStatus status;
    private Instant requestedAt;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionNote;
    /** The staff account this request produced, once approved. */
    private UUID createdStaffId;
}

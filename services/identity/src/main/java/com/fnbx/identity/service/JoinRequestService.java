package com.fnbx.identity.service;

import java.util.UUID;
import com.fnbx.identity.dto.request.ApproveJoinRequest;
import com.fnbx.identity.dto.request.RejectJoinRequest;
import com.fnbx.identity.dto.response.JoinRequestResponse;
import com.fnbx.identity.dto.response.StaffResponse;
import com.fnbx.identity.enums.JoinRequestStatus;
import com.fnbx.shared.utils.PagedResponse;

/**
 * Review of applications to join the business. ADMIN, at any active branch -
 * see docs/security/onboarding.md section 2.1.
 *
 * <p>Approval is the act that creates the staff account, so it is the only place in
 * the system where a person becomes an employee without an administrator having
 * typed their details in first.
 */
public interface JoinRequestService {

    /** Oldest first. A null status returns every request, decided ones included. */
    PagedResponse<JoinRequestResponse> list(JoinRequestStatus status, int page, int size);

    JoinRequestResponse get(UUID joinRequestId);

    /**
     * Creates the account from the application and assigns it in one transaction.
     * Refused if the request was already decided, if the branch is not this business's,
     * or if the caller lacks ADMIN authority.
     */
    StaffResponse approve(UUID joinRequestId, ApproveJoinRequest request);

    /** Records the refusal and its reason, and clears the stored credential. */
    JoinRequestResponse reject(UUID joinRequestId, RejectJoinRequest request);
}

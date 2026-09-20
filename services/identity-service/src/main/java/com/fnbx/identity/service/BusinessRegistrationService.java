package com.fnbx.identity.service;

import java.util.UUID;
import com.fnbx.identity.dto.request.VerifyBusinessRegistrationOtpRequest;
import com.fnbx.identity.dto.response.BusinessRegistrationVerificationResponse;
import com.fnbx.identity.dto.request.StartBusinessRegistrationRequest;
import com.fnbx.identity.dto.response.MessageResponse;
import com.fnbx.identity.dto.request.ApproveRegistrationRequest;
import com.fnbx.identity.dto.request.BusinessRegistrationRequest;
import com.fnbx.identity.dto.request.RejectRegistrationRequest;
import com.fnbx.identity.dto.response.BusinessRegistrationResponse;
import com.fnbx.identity.dto.response.RegistrationSubmittedResponse;
import com.fnbx.identity.enums.RegistrationStatus;
import com.fnbx.shared.utils.PagedResponse;

/** Email-verified public applications and platform-only review decisions. */
public interface BusinessRegistrationService {

    MessageResponse start(StartBusinessRegistrationRequest request);

    /** Trades the owner email OTP for a short-lived submission token. */
    BusinessRegistrationVerificationResponse verifyOtp(VerifyBusinessRegistrationOtpRequest request);

    /** Public. Files an application and returns nothing but a reference. */
    RegistrationSubmittedResponse submit(BusinessRegistrationRequest request);

    /** Oldest first. A null status returns every registration, decided ones included. */
    PagedResponse<BusinessRegistrationResponse> list(RegistrationStatus status, int page, int size);

    BusinessRegistrationResponse get(UUID registrationId);

    /**
     * Creates the business, its first branch and its owner from the application, in
     * one transaction, then emails the owner. Refused if the registration was already
     * decided or the owner email has not been verified.
     */
    BusinessRegistrationResponse approve(UUID registrationId, ApproveRegistrationRequest request);

    /** Records the refusal and emails the reason to the applicant verbatim. */
    BusinessRegistrationResponse reject(UUID registrationId, RejectRegistrationRequest request);
}

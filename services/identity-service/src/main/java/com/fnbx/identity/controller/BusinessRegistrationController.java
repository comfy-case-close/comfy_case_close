package com.fnbx.identity.controller;

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
import com.fnbx.identity.service.BusinessRegistrationService;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Public email-OTP submission and platform-key review. No public registration reads. */
@RestController
@RequestMapping("/businesses/registrations")
@Validated
public class BusinessRegistrationController {

    private final BusinessRegistrationService registrations;

    public BusinessRegistrationController(BusinessRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/start")
    public ResponseEntity<MessageResponse> start(
            @Valid @RequestBody StartBusinessRegistrationRequest request) {
        return ResponseEntity.ok(registrations.start(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<BusinessRegistrationVerificationResponse> verifyOtp(
            @Valid @RequestBody VerifyBusinessRegistrationOtpRequest request) {
        return ResponseEntity.ok(registrations.verifyOtp(request));
    }

    // ---- public -------------------------------------------------------------

    /**
     * Files an application. 202, not 201: nothing was created except a request for
     * somebody to look at, and the resource the applicant cares about - their business
     * - may never exist.
     */
    @PostMapping
    public ResponseEntity<RegistrationSubmittedResponse> submit(
            @Valid @RequestBody BusinessRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(registrations.submit(request));
    }

    // ---- platform -----------------------------------------------------------

    /** Oldest first. Omit {@code status} to see decided registrations as well. */
    @GetMapping
    public ResponseEntity<PagedResponse<BusinessRegistrationResponse>> list(
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(registrations.list(status, page, size));
    }

    @GetMapping("/{registrationId}")
    public ResponseEntity<BusinessRegistrationResponse> get(@PathVariable UUID registrationId) {
        return ResponseEntity.ok(registrations.get(registrationId));
    }

    /**
     * Creates the business, its branch and its owner, then emails the owner. 201,
     * because the meaningful result is a tenant that did not exist before.
     */
    @PostMapping("/{registrationId}/approve")
    public ResponseEntity<BusinessRegistrationResponse> approve(@PathVariable UUID registrationId,
            @Valid @RequestBody(required = false) ApproveRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.approve(registrationId, request));
    }

    /** The reason is required and is emailed to the applicant verbatim. */
    @PostMapping("/{registrationId}/reject")
    public ResponseEntity<BusinessRegistrationResponse> reject(@PathVariable UUID registrationId,
            @Valid @RequestBody RejectRegistrationRequest request) {
        return ResponseEntity.ok(registrations.reject(registrationId, request));
    }
}

package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.*;

/** Proves ownership of an owner email before a business application is submitted. */
public record VerifyBusinessRegistrationOtpRequest(
        @NotBlank @Email @Size(max = 255) String ownerEmail,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}

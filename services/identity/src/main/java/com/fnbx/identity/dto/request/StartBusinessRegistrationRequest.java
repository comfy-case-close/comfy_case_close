package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.*;

/** Request an OTP before submitting a business application. */
public record StartBusinessRegistrationRequest(@NotBlank @Email @Size(max = 255) String ownerEmail) {}

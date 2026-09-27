package com.fnbx.identity.dto.response;

/** Single-use proof required by business registration submission. */
public record BusinessRegistrationVerificationResponse(String registrationToken, long expiresInMs) {}

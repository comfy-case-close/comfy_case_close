package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
/** Exchanges a mailed code for a short-lived proof. OTPs are scoped to a business and a purpose. */
public record VerifyOtpRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}

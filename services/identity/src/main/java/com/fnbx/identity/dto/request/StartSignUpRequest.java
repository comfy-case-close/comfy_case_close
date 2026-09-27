package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
/** Send a tenant-scoped signup OTP. Select the tenant by its business code. */
public record StartSignUpRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email) {}

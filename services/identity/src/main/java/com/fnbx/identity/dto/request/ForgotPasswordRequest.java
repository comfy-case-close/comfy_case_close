package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
public record ForgotPasswordRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email) {}

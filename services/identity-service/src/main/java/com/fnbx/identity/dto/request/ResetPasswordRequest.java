package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email,
                                   @NotBlank @Size(max = 128) String resetToken,
                                   @NotBlank @Size(min = 8, max = 72) String newPassword) {}

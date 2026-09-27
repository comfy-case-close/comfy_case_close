package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                    @NotBlank @Size(min = 8, max = 72) String newPassword) {}

package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.*;

/** Business is a credential namespace here, never an authorization grant. */
public record LoginRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email,
                           @NotBlank @Size(max = 72) String password) {}

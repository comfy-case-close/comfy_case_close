package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
public record RefreshTokenRequest(@NotBlank @Size(max = 16384) String refreshToken) {}

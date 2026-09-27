package com.fnbx.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Short-lived proof that an email address completed password-reset OTP verification. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetVerificationResponse {

    private String resetToken;
    private long expiresInMs;
}

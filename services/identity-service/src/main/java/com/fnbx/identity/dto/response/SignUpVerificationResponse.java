package com.fnbx.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Short-lived proof that an email address completed signup OTP verification. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpVerificationResponse {

    private String signupToken;
    private long expiresInMs;
}

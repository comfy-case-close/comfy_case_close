package com.fnbx.identity.service;

import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.*;
import com.fnbx.shared.security.AccessPrincipal;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    MessageResponse logout(RefreshTokenRequest request);
    MessageResponse changePassword(AccessPrincipal caller, ChangePasswordRequest request);
    AuthUserResponse currentUser(AccessPrincipal caller);
    AuthUserResponse updateProfile(AccessPrincipal caller, UpdateProfileRequest request);
    MessageResponse startSignup(StartSignUpRequest request);
    SignUpVerificationResponse verifySignupOtp(VerifyOtpRequest request);

    /**
     * Activates a provisioned account, or files a join request when the address
     * belongs to nobody here. Returns {@link SignUpResponse} rather than
     * {@link AuthResponse} because those two endings are both ordinary outcomes -
     * see docs/security/onboarding.md sections 3.3 and 3.4.
     */
    SignUpResponse signup(SignUpRequest request);

    /** Signs in with Google, or files a join request. Same two endings as {@link #signup}. */
    SignUpResponse google(GoogleAuthRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);
    PasswordResetVerificationResponse verifyResetPasswordOtp(VerifyOtpRequest request);
    MessageResponse resetPassword(ResetPasswordRequest request);
}

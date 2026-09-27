package com.fnbx.identity.controller;

import com.fnbx.identity.dto.request.*;
import com.fnbx.identity.dto.response.*;
import com.fnbx.identity.enums.SignUpOutcome;
import com.fnbx.identity.service.AuthService;
import com.fnbx.shared.security.AccessPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping({"/login", "/signin"})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(auth.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(auth.logout(request));
    }

    @PostMapping("/signup/start")
    public ResponseEntity<MessageResponse> startSignup(@Valid @RequestBody StartSignUpRequest request) {
        return ResponseEntity.ok(auth.startSignup(request));
    }

    @PostMapping("/signup/verify")
    public ResponseEntity<SignUpVerificationResponse> verifySignup(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(auth.verifySignupOtp(request));
    }

    /**
     * 201 when an account was activated, 202 when a join request is waiting for
     * approval. The status mirrors {@code outcome} for clients that would rather read
     * it there, but the body is the contract - see
     * {@link com.fnbx.identity.dto.response.SignUpResponse}.
     */
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signup(@Valid @RequestBody SignUpRequest request) {
        SignUpResponse result = auth.signup(request);
        return ResponseEntity.status(status(result, HttpStatus.CREATED)).body(result);
    }

    /** Signs in, or files a join request for an address this business does not know. */
    @PostMapping("/google")
    public ResponseEntity<SignUpResponse> google(@Valid @RequestBody GoogleAuthRequest request) {
        SignUpResponse result = auth.google(request);
        return ResponseEntity.status(status(result, HttpStatus.OK)).body(result);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(auth.forgotPassword(request));
    }

    @PostMapping("/reset-password/verify")
    public ResponseEntity<PasswordResetVerificationResponse> verifyReset(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(auth.verifyResetPasswordOtp(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(auth.resetPassword(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(auth.changePassword(AccessPrincipal.current(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        return ResponseEntity.ok(auth.currentUser(AccessPrincipal.current()));
    }

    @PatchMapping("/me")
    public ResponseEntity<AuthUserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(auth.updateProfile(AccessPrincipal.current(), request));
    }

    private static HttpStatus status(SignUpResponse result, HttpStatus issued) {
        return result.getOutcome() == SignUpOutcome.PENDING_APPROVAL ? HttpStatus.ACCEPTED : issued;
    }
}

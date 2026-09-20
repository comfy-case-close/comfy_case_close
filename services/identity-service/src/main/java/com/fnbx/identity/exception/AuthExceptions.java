package com.fnbx.identity.exception;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

/** Identity failures; stable codes and HTTP statuses live in the shared catalog. */
public final class AuthExceptions {
    private AuthExceptions() {}
    public static AppException invalidCredentials() {
        return new AppException(ErrorCode.INVALID_CREDENTIALS);
    }
    public static AppException accountDisabled() {
        return new AppException(ErrorCode.ACCOUNT_DISABLED);
    }
    public static AppException invalidOtp() {
        return new AppException(ErrorCode.INVALID_OTP);
    }
    public static AppException otpExpired() {
        return new AppException(ErrorCode.OTP_EXPIRED);
    }
    public static AppException otpRequestTooSoon() {
        return new AppException(ErrorCode.OTP_REQUEST_TOO_SOON);
    }
    public static AppException tooManyOtpAttempts() {
        return new AppException(ErrorCode.TOO_MANY_OTP_ATTEMPTS);
    }
    public static AppException currentPasswordIncorrect() {
        return new AppException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
    }
    public static AppException invalidToken() {
        return new AppException(ErrorCode.INVALID_TOKEN);
    }
    public static AppException refreshTokenRotated() {
        return new AppException(ErrorCode.REFRESH_TOKEN_ROTATED);
    }
    public static AppException invalidGoogleToken() {
        return new AppException(ErrorCode.INVALID_GOOGLE_TOKEN);
    }
    public static AppException emailNotVerified() {
        return new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
    }
    public static AppException invalidSignupToken() {
        return new AppException(ErrorCode.INVALID_SIGNUP_TOKEN);
    }
    public static AppException invalidPasswordResetToken() {
        return new AppException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }
    public static AppException emailAlreadyExists() {
        return new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
    public static AppException deliveryUnavailable() {
        return new AppException(ErrorCode.DELIVERY_UNAVAILABLE);
    }
    public static AppException noActiveBranchAssignment() {
        return new AppException(ErrorCode.ACCESS_DENIED, "No active branch assignment");
    }
    public static AppException requiredField(String field) {
        return new AppException(ErrorCode.VALIDATION_FAILED, field + " is required");
    }
    public static AppException passwordTooLong() {
        return new AppException(ErrorCode.VALIDATION_FAILED, "Password must not exceed 72 UTF-8 bytes");
    }
}

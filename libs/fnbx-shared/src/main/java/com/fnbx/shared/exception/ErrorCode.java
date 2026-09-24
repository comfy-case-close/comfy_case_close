package com.fnbx.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Stable application-wide error catalog. Never reuse a published numeric code. */
@Getter
public enum ErrorCode {
    // Common request errors (20xx)
    VALIDATION_FAILED(2000, HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(2001, HttpStatus.BAD_REQUEST, "Malformed request"),
    RESOURCE_NOT_FOUND(2002, HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(2003, HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(2004, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    NOT_ACCEPTABLE(2005, HttpStatus.NOT_ACCEPTABLE, "Requested response format is not supported"),
    RESOURCE_CONFLICT(2006, HttpStatus.CONFLICT, "Resource conflict"),
    DATE_RANGE_INVALID(2007, HttpStatus.UNPROCESSABLE_ENTITY, "fromDate must be on or before toDate"),

    // Authentication and authorization (24xx): preserve published codes
    EMAIL_ALREADY_EXISTS(2401, HttpStatus.CONFLICT, "Account already exists"),
    INVALID_CREDENTIALS(2402, HttpStatus.UNAUTHORIZED, "Invalid account or password"),
    ACCOUNT_DISABLED(2403, HttpStatus.FORBIDDEN, "Account is disabled"),
    INVALID_OTP(2404, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid verification code"),
    OTP_EXPIRED(2405, HttpStatus.UNPROCESSABLE_ENTITY, "Verification code expired"),
    CURRENT_PASSWORD_INCORRECT(2406, HttpStatus.UNPROCESSABLE_ENTITY, "Current password is incorrect"),
    INVALID_TOKEN(2407, HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    INVALID_GOOGLE_TOKEN(2408, HttpStatus.UNAUTHORIZED, "Invalid Google credential"),
    UNAUTHENTICATED(2409, HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(2410, HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    EMAIL_NOT_VERIFIED(2411, HttpStatus.FORBIDDEN, "Email is not verified"),
    INVALID_SIGNUP_TOKEN(2412, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or expired signup token"),
    INVALID_PASSWORD_RESET_TOKEN(2415, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or expired password reset token"),
    OTP_REQUEST_TOO_SOON(2416, HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code"),
    TOO_MANY_OTP_ATTEMPTS(2417, HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts"),
    REFRESH_TOKEN_ROTATED(2418, HttpStatus.CONFLICT, "This refresh token was just rotated. Retry with the newest one."),
    DELIVERY_UNAVAILABLE(2419, HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is temporarily unavailable"),

    // Onboarding and tenant administration (242x): business provisioning, join
    // requests and branch assignment. See docs/security/onboarding.md.
    BUSINESS_NOT_FOUND(2420, HttpStatus.NOT_FOUND, "Business not found"),
    BUSINESS_CODE_TAKEN(2421, HttpStatus.CONFLICT, "Business code is already in use"),
    BRANCH_NOT_FOUND(2422, HttpStatus.NOT_FOUND, "Branch not found"),
    BRANCH_CODE_TAKEN(2423, HttpStatus.CONFLICT, "Branch code is already in use in this business"),
    OWNER_ALREADY_EXISTS(2424, HttpStatus.CONFLICT, "This business already has an owner"),
    JOIN_REQUEST_NOT_FOUND(2425, HttpStatus.NOT_FOUND, "Join request not found"),
    JOIN_REQUEST_ALREADY_DECIDED(2426, HttpStatus.CONFLICT, "This join request has already been decided"),
    STAFF_NOT_FOUND(2427, HttpStatus.NOT_FOUND, "Staff member not found"),
    LAST_ACTIVE_BRANCH(2428, HttpStatus.UNPROCESSABLE_ENTITY, "A business must keep at least one active branch"),
    ROLE_GRANT_DENIED(2429, HttpStatus.FORBIDDEN, "You may not grant this role"),
    LAST_ACTIVE_ADMIN(2430, HttpStatus.UNPROCESSABLE_ENTITY, "A business must keep at least one active ADMIN"),
    REGISTRATION_NOT_FOUND(2431, HttpStatus.NOT_FOUND, "Business registration not found"),
    REGISTRATION_ALREADY_DECIDED(2432, HttpStatus.CONFLICT, "This registration has already been decided"),
    REGISTRATION_PENDING_EXISTS(2433, HttpStatus.CONFLICT, "A registration for this business code or email is already awaiting review"),

    INVALID_REGISTRATION_TOKEN(2434, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or expired business registration token"),

    // Cash-close domain (30xx)
    CLOSE_ALREADY_EXISTS(3001, HttpStatus.UNPROCESSABLE_ENTITY, "A live close already exists for this branch, shift and date"),
    NO_DENOMINATION_COUNT(3002, HttpStatus.UNPROCESSABLE_ENTITY, "No denomination count entered - cannot submit"),
    CLOSE_VALIDATION_FAILED(3003, HttpStatus.UNPROCESSABLE_ENTITY, "Cash close validation failed"),
    MOVEMENTS_PENDING(3004, HttpStatus.UNPROCESSABLE_ENTITY, "Movement lines are still pending - approve or reject each one first"),
    REASON_REQUIRED(3005, HttpStatus.UNPROCESSABLE_ENTITY, "A reason is required"),
    CLOSE_FROZEN(3006, HttpStatus.UNPROCESSABLE_ENTITY, "Cash close is frozen"),
    MOVEMENT_DECIDED(3007, HttpStatus.UNPROCESSABLE_ENTITY, "Movement has already been decided"),
    UNKNOWN_MOVEMENT_KIND(3008, HttpStatus.UNPROCESSABLE_ENTITY, "Unknown movement kind"),
    ILLEGAL_TRANSITION(3009, HttpStatus.UNPROCESSABLE_ENTITY, "Illegal cash close transition"),
    ILLEGAL_MOVEMENT_TRANSITION(3010, HttpStatus.UNPROCESSABLE_ENTITY, "Illegal movement transition"),
    INVALID_FILTER(3011, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid filter"),
    CASH_CLOSE_NOT_FOUND(3012, HttpStatus.NOT_FOUND, "Cash close not found"),
    MOVEMENT_NOT_FOUND(3013, HttpStatus.NOT_FOUND, "Movement line not found"),

    // 3014 is CONFLICT, not UNPROCESSABLE_ENTITY, and deliberately so. 422 says
    // "the request was understood but is wrong"; editing a close that somebody has
    // already submitted is not a wrong request, it is a request that arrived too
    // late - the resource moved underneath it. 409 is the status a client can
    // retry-after-refresh on, which is exactly the recovery this case has.
    // The 422 codes above keep their published status; none is restated here.
    CLOSE_NOT_DRAFT(3014, HttpStatus.CONFLICT, "Cash close is no longer a draft"),
    UNKNOWN_DENOMINATION(3015, HttpStatus.UNPROCESSABLE_ENTITY, "Unknown or inactive denomination"),
    EXPECTED_CASH_LOCKED(3016, HttpStatus.UNPROCESSABLE_ENTITY, "Expected cash came from the POS and cannot be typed in"),
    DUPLICATE_DENOMINATION(3017, HttpStatus.UNPROCESSABLE_ENTITY, "The same denomination was counted twice"),
    BRANCH_HEADER_MISMATCH(3018, HttpStatus.FORBIDDEN, "This close belongs to a different branch"),

    // Infrastructure failures (9xxx)
    SERVICE_UNAVAILABLE(9001, HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable"),
    UNEXPECTED_ERROR(9999, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    /** Maps framework HTTP failures to safe public messages, never exception details. */
    public static ErrorCode forHttpStatus(int status) {
        return switch (status) {
            case 400, 422 -> VALIDATION_FAILED;
            case 401 -> UNAUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> RESOURCE_CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> UNEXPECTED_ERROR;
        };
    }
}

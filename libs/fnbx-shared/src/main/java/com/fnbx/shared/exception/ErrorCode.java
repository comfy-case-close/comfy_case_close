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
    RATE_LIMITED(2008, HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later"),

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

    UNKNOWN_DENOMINATION(3015, HttpStatus.UNPROCESSABLE_ENTITY, "Unknown or inactive denomination"),
    EXPECTED_CASH_LOCKED(3016, HttpStatus.UNPROCESSABLE_ENTITY, "Expected cash came from the POS and cannot be typed in"),
    DUPLICATE_DENOMINATION(3017, HttpStatus.UNPROCESSABLE_ENTITY, "The same denomination was counted twice"),
    BRANCH_HEADER_MISMATCH(3018, HttpStatus.FORBIDDEN, "This close belongs to a different branch"),

    WITHDRAWAL_CONFIRMATION_REQUIRED(3019, HttpStatus.UNPROCESSABLE_ENTITY,
            "The current withdrawal must be confirmed by the named withdrawing person before close approval"),

    // Payroll domain (40xx)
    PERIOD_NOT_FOUND(4000, HttpStatus.NOT_FOUND, "Payroll period not found"),
    PERIOD_LOCKED(4001, HttpStatus.CONFLICT, "The payroll period is locked"),
    STALE_CALCULATION(4002, HttpStatus.CONFLICT, "Timesheet data changed after the latest payroll run"),
    VERSION_CONFLICT(4003, HttpStatus.CONFLICT, "The record was changed by someone else"),
    ASSIGNMENT_OVERLAP(4004, HttpStatus.CONFLICT, "Contract overlaps another one for the same person and position"),
    UNACKNOWLEDGED_WARNINGS(4005, HttpStatus.CONFLICT, "Unacknowledged warnings remain"),
    BRANCH_NOT_ALLOWED(4006, HttpStatus.BAD_REQUEST, "The contract may not be rostered at this branch"),
    EMPLOYEE_NOT_FOUND(4007, HttpStatus.NOT_FOUND, "Employee not found"),
    ASSIGNMENT_NOT_FOUND(4008, HttpStatus.NOT_FOUND, "Contract not found"),
    PAYROLL_LINE_NOT_FOUND(4009, HttpStatus.NOT_FOUND, "Payroll line not found"),
    PERIOD_NOT_DRAFT(4010, HttpStatus.CONFLICT, "The payroll period is no longer a draft"),
    PAYROLL_RUN_NOT_FOUND(4011, HttpStatus.NOT_FOUND, "Payroll run not found"),
    PAYSLIP_NOT_FOUND(4012, HttpStatus.NOT_FOUND, "Payslip not found"),
    MANUAL_ITEM_NOT_ALLOWED(4013, HttpStatus.BAD_REQUEST, "The component is not a manual item, or a negative amount is not allowed"),
    LEAVE_QUOTA_NOT_FOUND(4014, HttpStatus.NOT_FOUND, "Leave quota not found"),
    CONTRACT_RATE_MISSING(4015, HttpStatus.BAD_REQUEST, "The contract has no pay rate"),
    DUPLICATE_PERIOD(4016, HttpStatus.CONFLICT, "A payroll period already exists for this year and month"),
    ISSUE_NOT_FOUND(4017, HttpStatus.NOT_FOUND, "Validation issue not found"),
    ISSUE_NOT_ACKNOWLEDGEABLE(4018, HttpStatus.BAD_REQUEST, "Only a warning can be acknowledged"),
    PAYROLL_LINE_HAS_TIMESHEET(4020, HttpStatus.CONFLICT, "The payroll line holds timesheet cells or manual items"),
    DUPLICATE_PAYROLL_LINE(4021, HttpStatus.CONFLICT, "The contract is already rostered at this branch in this period"),
    WORK_DATE_OUTSIDE_PERIOD(4022, HttpStatus.BAD_REQUEST, "The work date is outside the payroll period"),

    INSURANCE_BASE_EXCEEDS_SALARY(4030, HttpStatus.UNPROCESSABLE_ENTITY, "The insurance base exceeds the agreed salary"),
    PAYSLIP_PERIOD_NOT_PAID(4032, HttpStatus.CONFLICT, "Payslips can only be sent after the period is marked paid"),
    PERIOD_HAS_PAID_PAYMENTS(4033, HttpStatus.CONFLICT, "The period already has paid salary transfers"),
    PERIOD_DATES_OVERLAP(4034, HttpStatus.CONFLICT, "The date range overlaps another payroll period"),
    PAYMENT_NOT_FOUND(4035, HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_BANK_INFO_MISSING(4036, HttpStatus.UNPROCESSABLE_ENTITY, "The bank account of this employee is incomplete"),
    PERIOD_HAS_OPEN_PAYMENTS(4037, HttpStatus.CONFLICT, "Some salary transfers of this period are not paid yet"),
    CONFIRMATION_TOKEN_INVALID(4038, HttpStatus.NOT_FOUND, "This confirmation link is invalid or has expired"),
    PERIOD_DATES_INVALID(4039, HttpStatus.UNPROCESSABLE_ENTITY, "The period must start before it ends and last at most 62 days"),
    IMPORT_JOB_NOT_FOUND(4040, HttpStatus.NOT_FOUND, "Import job not found"),
    IMPORT_FILE_INVALID(4041, HttpStatus.UNPROCESSABLE_ENTITY, "The import file cannot be read"),
    IMPORT_TOO_MANY_ROWS(4042, HttpStatus.UNPROCESSABLE_ENTITY, "The import file has too many rows"),
    IMPORT_NOT_COMMITTABLE(4043, HttpStatus.CONFLICT, "This import cannot be committed"),
    CONTRACT_TEMPLATE_NOT_FOUND(4044, HttpStatus.NOT_FOUND, "Contract template not found"),
    CONTRACT_DOCUMENT_NOT_FOUND(4045, HttpStatus.NOT_FOUND, "Contract document not found"),
    CONTRACT_BATCH_NOT_FOUND(4046, HttpStatus.NOT_FOUND, "Contract batch not found"),
    CONTRACT_PROFILE_INCOMPLETE(4047, HttpStatus.UNPROCESSABLE_ENTITY, "The employee profile lacks data required to print a contract"),

    // Shift scheduling (41xx)
    SHIFT_SCHEDULE_NOT_FOUND(4100, HttpStatus.NOT_FOUND, "Shift schedule not found"),
    SCHEDULE_LOCKED(4101, HttpStatus.CONFLICT, "The week is locked"),
    SHIFT_ASSIGNMENT_NOT_FOUND(4104, HttpStatus.NOT_FOUND, "Shift assignment not found"),
    SHIFT_SLOT_NOT_FOUND(4105, HttpStatus.NOT_FOUND, "Shift slot not found"),
    ATTENDANCE_SHEET_NOT_FOUND(4106, HttpStatus.NOT_FOUND, "Attendance sheet not found"),
    GENERATION_RUN_NOT_FOUND(4107, HttpStatus.NOT_FOUND, "Auto-fill run not found"),
    REGISTRATION_CLOSED(4110, HttpStatus.CONFLICT, "Registration is not open for this week"),
    STAFF_NOT_ELIGIBLE(4120, HttpStatus.UNPROCESSABLE_ENTITY, "The employee does not work at this branch or cannot fill this position"),
    BRANCH_CONFLICT_SAME_DAY(4130, HttpStatus.CONFLICT, "The employee already works at another branch that day"),
    SHIFT_OVERLAP(4131, HttpStatus.CONFLICT, "The employee already has an overlapping shift"),
    SCHEDULE_STATE_INVALID(4160, HttpStatus.CONFLICT, "The schedule status does not allow this action"),
    PUBLISHED_ADD_FORBIDDEN(4161, HttpStatus.FORBIDDEN, "Only a general manager can add people or change shift times after approval"),
    MINIMUM_STAFFING_VIOLATION(4162, HttpStatus.UNPROCESSABLE_ENTITY, "A time window would fall below the minimum staffing"),
    ATTENDANCE_PERIOD_CLOSED(4170, HttpStatus.CONFLICT, "The payroll period is locked and cannot receive attendance"),
    ATTENDANCE_NO_PAYROLL_LINE(4171, HttpStatus.UNPROCESSABLE_ENTITY, "The employee has no payroll line at this branch"),
    ATTENDANCE_STATE_INVALID(4180, HttpStatus.CONFLICT, "The attendance sheet status does not allow this action"),
    ATTENDANCE_WEEK_NOT_ENDED(4181, HttpStatus.CONFLICT, "Attendance can only be created after the week has ended"),
    ATTENDANCE_LEAVE_NOT_ALLOWED(4182, HttpStatus.UNPROCESSABLE_ENTITY, "Leave can only be marked for full-time employees"),

    // Infrastructure failures (9xxx)
    SERVICE_UNAVAILABLE(9001, HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable"),
    FEATURE_NOT_AVAILABLE(9002, HttpStatus.NOT_IMPLEMENTED, "Feature not available"),
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

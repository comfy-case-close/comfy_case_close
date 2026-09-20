package com.fnbx.identity.exception;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

/**
 * Provisioning and membership failures. Separate from {@link AuthExceptions}
 * because these are not authentication outcomes - the caller is already
 * authenticated, or is the platform administrator; what failed is an
 * administrative rule. Stable codes and HTTP statuses live in the shared catalog.
 */
public final class OnboardingExceptions {
    private OnboardingExceptions() {}

    public static AppException businessNotFound() {
        return new AppException(ErrorCode.BUSINESS_NOT_FOUND);
    }
    public static AppException businessCodeTaken() {
        return new AppException(ErrorCode.BUSINESS_CODE_TAKEN);
    }
    public static AppException branchNotFound() {
        return new AppException(ErrorCode.BRANCH_NOT_FOUND);
    }
    public static AppException branchCodeTaken() {
        return new AppException(ErrorCode.BRANCH_CODE_TAKEN);
    }
    public static AppException ownerAlreadyExists() {
        return new AppException(ErrorCode.OWNER_ALREADY_EXISTS);
    }
    public static AppException joinRequestNotFound() {
        return new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
    }
    public static AppException joinRequestAlreadyDecided() {
        return new AppException(ErrorCode.JOIN_REQUEST_ALREADY_DECIDED);
    }
    public static AppException staffNotFound() {
        return new AppException(ErrorCode.STAFF_NOT_FOUND);
    }
    public static AppException lastActiveBranch() {
        return new AppException(ErrorCode.LAST_ACTIVE_BRANCH);
    }
    public static AppException invalidRegistrationToken() {
        return new AppException(ErrorCode.INVALID_REGISTRATION_TOKEN);
    }
    public static AppException registrationNotFound() {
        return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
    }
    public static AppException registrationAlreadyDecided() {
        return new AppException(ErrorCode.REGISTRATION_ALREADY_DECIDED);
    }

    /**
     * A registration for this business code or this owner address is already in the
     * queue. Which of the two is not disclosed: the caller is anonymous, and saying
     * "that email already applied" would make the route a probe.
     */
    public static AppException registrationPendingExists() {
        return new AppException(ErrorCode.REGISTRATION_PENDING_EXISTS);
    }

    /** Only an ADMIN may grant ADMIN. Without this, any HR could promote a colleague. */
    public static AppException adminGrantRequiresAdmin() {
        return new AppException(ErrorCode.ROLE_GRANT_DENIED, "Only an ADMIN may grant or withdraw the ADMIN role");
    }

    /**
     * The business would be left with nobody in charge. Only a platform administrator
     * could repair that, so it is refused rather than performed.
     */
    public static AppException lastActiveAdmin() {
        return new AppException(ErrorCode.LAST_ACTIVE_ADMIN);
    }
}

package com.fnbx.cashclose.exception;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

/** Domain factories. Missing and inaccessible resources deliberately produce the same 404. */
public final class CashCloseExceptions {
    private CashCloseExceptions() {}
    public static AppException closeAlreadyExists() {
        return new AppException(ErrorCode.CLOSE_ALREADY_EXISTS);
    }
    public static AppException noDenominationCount() {
        return new AppException(ErrorCode.NO_DENOMINATION_COUNT);
    }
    public static AppException validationFailed(String message) {
        return new AppException(ErrorCode.CLOSE_VALIDATION_FAILED, message);
    }
    public static AppException movementsPending() {
        return new AppException(ErrorCode.MOVEMENTS_PENDING);
    }
    public static AppException reasonRequired(String message) {
        return new AppException(ErrorCode.REASON_REQUIRED, message);
    }
    public static AppException closeFrozen(String message) {
        return new AppException(ErrorCode.CLOSE_FROZEN, message);
    }
    public static AppException movementDecided(String message) {
        return new AppException(ErrorCode.MOVEMENT_DECIDED, message);
    }
    public static AppException unknownMovementKind(String message) {
        return new AppException(ErrorCode.UNKNOWN_MOVEMENT_KIND, message);
    }
    public static AppException illegalTransition(String message) {
        return new AppException(ErrorCode.ILLEGAL_TRANSITION, message);
    }
    public static AppException illegalMovementTransition(String message) {
        return new AppException(ErrorCode.ILLEGAL_MOVEMENT_TRANSITION, message);
    }
    public static AppException invalidFilter(String message) {
        return new AppException(ErrorCode.INVALID_FILTER, message);
    }
    public static AppException cashCloseNotFound() {
        return new AppException(ErrorCode.CASH_CLOSE_NOT_FOUND);
    }
    public static AppException movementNotFound() {
        return new AppException(ErrorCode.MOVEMENT_NOT_FOUND);
    }
    /** 409, not 422: the request was fine, the document moved on before it arrived. */
    public static AppException closeNotDraft(String message) {
        return new AppException(ErrorCode.CLOSE_NOT_DRAFT, message);
    }
    public static AppException unknownDenomination(String message) {
        return new AppException(ErrorCode.UNKNOWN_DENOMINATION, message);
    }
    public static AppException duplicateDenomination(String message) {
        return new AppException(ErrorCode.DUPLICATE_DENOMINATION, message);
    }
    public static AppException expectedCashLocked(String message) {
        return new AppException(ErrorCode.EXPECTED_CASH_LOCKED, message);
    }
    public static AppException branchHeaderMismatch() {
        return new AppException(ErrorCode.BRANCH_HEADER_MISMATCH);
    }
}

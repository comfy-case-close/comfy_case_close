package com.fnbx.hrm.exception;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

public final class PayrollExceptions {

    private PayrollExceptions() {}

    public static AppException periodNotFound() {
        return new AppException(ErrorCode.PERIOD_NOT_FOUND);
    }

    public static AppException periodLocked(String message) {
        return new AppException(ErrorCode.PERIOD_LOCKED, message);
    }

    public static AppException periodNotDraft(String message) {
        return new AppException(ErrorCode.PERIOD_NOT_DRAFT, message);
    }

    public static AppException duplicatePeriod() {
        return new AppException(ErrorCode.DUPLICATE_PERIOD);
    }

    public static AppException staleCalculation() {
        return new AppException(ErrorCode.STALE_CALCULATION);
    }

    public static AppException versionConflict() {
        return new AppException(ErrorCode.VERSION_CONFLICT);
    }

    public static AppException assignmentOverlap() {
        return new AppException(ErrorCode.ASSIGNMENT_OVERLAP);
    }

    public static AppException unacknowledgedWarnings() {
        return new AppException(ErrorCode.UNACKNOWLEDGED_WARNINGS);
    }

    public static AppException branchNotAllowed(String message) {
        return new AppException(ErrorCode.BRANCH_NOT_ALLOWED, message);
    }

    public static AppException employeeNotFound() {
        return new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    public static AppException assignmentNotFound() {
        return new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
    }

    public static AppException payrollLineNotFound() {
        return new AppException(ErrorCode.PAYROLL_LINE_NOT_FOUND);
    }

    public static AppException payrollRunNotFound() {
        return new AppException(ErrorCode.PAYROLL_RUN_NOT_FOUND);
    }

    public static AppException payslipNotFound() {
        return new AppException(ErrorCode.PAYSLIP_NOT_FOUND);
    }

    public static AppException manualItemNotAllowed(String message) {
        return new AppException(ErrorCode.MANUAL_ITEM_NOT_ALLOWED, message);
    }

    public static AppException leaveQuotaNotFound() {
        return new AppException(ErrorCode.LEAVE_QUOTA_NOT_FOUND);
    }

    public static AppException contractRateMissing() {
        return new AppException(ErrorCode.CONTRACT_RATE_MISSING);
    }

    public static AppException issueNotFound() {
        return new AppException(ErrorCode.ISSUE_NOT_FOUND);
    }

    public static AppException issueNotAcknowledgeable() {
        return new AppException(ErrorCode.ISSUE_NOT_ACKNOWLEDGEABLE);
    }

    public static AppException payrollLineHasTimesheet() {
        return new AppException(ErrorCode.PAYROLL_LINE_HAS_TIMESHEET);
    }

    public static AppException duplicatePayrollLine() {
        return new AppException(ErrorCode.DUPLICATE_PAYROLL_LINE);
    }

    public static AppException workDateOutsidePeriod() {
        return new AppException(ErrorCode.WORK_DATE_OUTSIDE_PERIOD);
    }

    public static AppException resourceNotFound() {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    public static AppException resourceConflict(String message) {
        return new AppException(ErrorCode.RESOURCE_CONFLICT, message);
    }

    public static AppException featureNotAvailable(String message) {
        return new AppException(ErrorCode.FEATURE_NOT_AVAILABLE, message);
    }

    public static AppException invalidField(String message) {
        return new AppException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static AppException insuranceBaseExceedsSalary() {
        return new AppException(ErrorCode.INSURANCE_BASE_EXCEEDS_SALARY);
    }

    public static AppException paymentNotFound() {
        return new AppException(ErrorCode.PAYMENT_NOT_FOUND);
    }

    public static AppException paymentBankInfoMissing() {
        return new AppException(ErrorCode.PAYMENT_BANK_INFO_MISSING);
    }

    public static AppException periodHasPaidPayments() {
        return new AppException(ErrorCode.PERIOD_HAS_PAID_PAYMENTS);
    }

    public static AppException periodHasOpenPayments() {
        return new AppException(ErrorCode.PERIOD_HAS_OPEN_PAYMENTS);
    }

    public static AppException payslipPeriodNotPaid() {
        return new AppException(ErrorCode.PAYSLIP_PERIOD_NOT_PAID);
    }

    public static AppException confirmationTokenInvalid() {
        return new AppException(ErrorCode.CONFIRMATION_TOKEN_INVALID);
    }

    public static AppException periodDatesInvalid() {
        return new AppException(ErrorCode.PERIOD_DATES_INVALID);
    }

    public static AppException periodDatesOverlap() {
        return new AppException(ErrorCode.PERIOD_DATES_OVERLAP);
    }

    public static AppException shiftSlotNotFound() {
        return new AppException(ErrorCode.SHIFT_SLOT_NOT_FOUND);
    }

    public static AppException scheduleNotFound() {
        return new AppException(ErrorCode.SHIFT_SCHEDULE_NOT_FOUND);
    }

    public static AppException shiftAssignmentNotFound() {
        return new AppException(ErrorCode.SHIFT_ASSIGNMENT_NOT_FOUND);
    }

    public static AppException attendanceSheetNotFound() {
        return new AppException(ErrorCode.ATTENDANCE_SHEET_NOT_FOUND);
    }

    public static AppException generationRunNotFound() {
        return new AppException(ErrorCode.GENERATION_RUN_NOT_FOUND);
    }

    public static AppException registrationClosed() {
        return new AppException(ErrorCode.REGISTRATION_CLOSED);
    }

    public static AppException shiftOverlap(String message) {
        return new AppException(ErrorCode.SHIFT_OVERLAP, message);
    }

    public static AppException staffNotEligible(String message) {
        return new AppException(ErrorCode.STAFF_NOT_ELIGIBLE, message);
    }

    public static AppException branchConflictSameDay(String message) {
        return new AppException(ErrorCode.BRANCH_CONFLICT_SAME_DAY, message);
    }

    public static AppException scheduleStateInvalid(String message) {
        return new AppException(ErrorCode.SCHEDULE_STATE_INVALID, message);
    }

    public static AppException minimumStaffingViolation(String message) {
        return new AppException(ErrorCode.MINIMUM_STAFFING_VIOLATION, message);
    }

    public static AppException attendancePeriodClosed(String message) {
        return new AppException(ErrorCode.ATTENDANCE_PERIOD_CLOSED, message);
    }

    public static AppException attendanceNoPayrollLine(String message) {
        return new AppException(ErrorCode.ATTENDANCE_NO_PAYROLL_LINE, message);
    }

    public static AppException attendanceStateInvalid(String message) {
        return new AppException(ErrorCode.ATTENDANCE_STATE_INVALID, message);
    }

    public static AppException publishedAddForbidden() {
        return new AppException(ErrorCode.PUBLISHED_ADD_FORBIDDEN);
    }

    public static AppException scheduleLocked() {
        return new AppException(ErrorCode.SCHEDULE_LOCKED);
    }

    public static AppException attendanceWeekNotEnded() {
        return new AppException(ErrorCode.ATTENDANCE_WEEK_NOT_ENDED);
    }

    public static AppException attendanceLeaveNotAllowed() {
        return new AppException(ErrorCode.ATTENDANCE_LEAVE_NOT_ALLOWED);
    }
}

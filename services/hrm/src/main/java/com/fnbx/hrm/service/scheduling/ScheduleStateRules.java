package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.enums.ScheduleStatus;
import com.fnbx.hrm.exception.PayrollExceptions;

/** What each schedule status allows, for the store manager and for the general manager. */
public final class ScheduleStateRules {

    private ScheduleStateRules() {}

    /** Adding people or changing shift times. */
    public static void requireArrangeable(ScheduleStatus status, boolean approver) {
        switch (status) {
            case DRAFT -> { }
            case PENDING_APPROVAL -> requireApprover(approver);
            case PUBLISHED -> {
                if (!approver) {
                    throw PayrollExceptions.publishedAddForbidden();
                }
            }
            case LOCKED -> throw PayrollExceptions.scheduleLocked();
        }
    }

    /** Removing or replacing people. */
    public static void requireTrimmable(ScheduleStatus status, boolean approver) {
        switch (status) {
            case DRAFT, PUBLISHED -> { }
            case PENDING_APPROVAL -> requireApprover(approver);
            case LOCKED -> throw PayrollExceptions.scheduleLocked();
        }
    }

    public static void requireStatus(ScheduleStatus actual, ScheduleStatus expected, String action) {
        if (actual != expected) {
            throw PayrollExceptions.scheduleStateInvalid("The schedule is " + actual + ", cannot " + action);
        }
    }

    /** A published schedule stays frozen to the store manager unless only people are replaced or removed. */
    public static boolean needsMinimumStaffingCheck(ScheduleStatus status, boolean approver) {
        return status == ScheduleStatus.PUBLISHED && !approver;
    }

    private static void requireApprover(boolean approver) {
        if (!approver) {
            throw PayrollExceptions.scheduleStateInvalid("Only a general manager can change a schedule that awaits approval");
        }
    }
}

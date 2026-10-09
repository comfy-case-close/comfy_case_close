package com.fnbx.hrm.enums;

/**
 * Shared by {@code payroll_run.status} and {@code import_job.status} - both
 * are "one attempt at a batch operation, may fail". A FAILED payroll run is a
 * normal outcome, not an exception (spec section 9.4): it always returns
 * {@code 200} with this status plus the {@code data_validation_issue} rows
 * that caused it.
 */
public enum RunStatus {
    RUNNING, SUCCEEDED, FAILED
}

package com.fnbx.hrm.enums;

/**
 * {@code ERROR} blocks a payroll run and rolls the period back to
 * {@code DRAFT}; {@code WARNING} only blocks locking the period, and must be
 * explicitly acknowledged first (spec section 5.10, 8.11).
 */
public enum IssueSeverity {
    ERROR, WARNING
}

package com.fnbx.hrm.enums;

/**
 * The set of sibling {@code payroll_line} rows a {@link ProrationBasis}
 * splits an allowance across (spec section 5.5.1).
 *
 * <ul>
 *   <li>{@code LINE} - no sharing; the line's own value only.</li>
 *   <li>{@code ASSIGNMENT} - lines of the same contract (person + position +
 *       employment type) in the period. Used by {@code RESP_ALW}.</li>
 *   <li>{@code EMPLOYEE} - every line of that person in the period, FULLTIME
 *       and PARTTIME together. Used by the 4 person-level allowances.</li>
 * </ul>
 */
public enum ShareScope {
    LINE, ASSIGNMENT, EMPLOYEE
}

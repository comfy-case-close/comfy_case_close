package com.fnbx.hrm.enums;

/**
 * Which {@link EmploymentType} a {@link com.fnbx.hrm.entity.PayComponent}
 * applies to. {@code BOTH} on the 4 person-level allowances is
 * <b>DEC-01 - tentative</b>: the source workbook never actually paid them to
 * part-timers (missing formulas, not a deliberate rule - formula reference
 * section 16.1). Revisit before entering an allowance for any part-timer.
 */
public enum AppliesTo {
    FULLTIME, PARTTIME, BOTH
}

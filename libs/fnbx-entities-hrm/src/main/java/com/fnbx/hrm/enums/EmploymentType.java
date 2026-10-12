package com.fnbx.hrm.enums;

/**
 * FULLTIME is paid by workdays (hours / standard_hours_per_day), PARTTIME by
 * raw hours. Same Excel column ({@code AR}) carried both units in the source
 * workbook - {@link com.fnbx.hrm.entity.PayrollLine} keeps them as two
 * separate fields ({@code regularHours}, {@code standardWorkdays}) instead.
 */
public enum EmploymentType {
    FULLTIME, PARTTIME
}

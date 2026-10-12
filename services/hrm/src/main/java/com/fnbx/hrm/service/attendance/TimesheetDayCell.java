package com.fnbx.hrm.service.attendance;

import java.math.BigDecimal;

/** The timesheet cell one person gets for one day, or no cell when {@code rawValue} is null. */
public record TimesheetDayCell(String rawValue, int lateShifts, BigDecimal payableHours) {
}

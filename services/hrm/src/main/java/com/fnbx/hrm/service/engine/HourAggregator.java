package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.TimesheetEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hour totals for one payroll line, from its timesheet entries (spec section
 * 5.4). Pure, so the same computation backs both the live timesheet grid and
 * the payroll engine - one formula, not two that can drift.
 */
@Component
public class HourAggregator {

    public record LineHours(BigDecimal totalHours, BigDecimal standardHours, BigDecimal overtimeHours,
                             BigDecimal weekendHours, BigDecimal regularHours, BigDecimal standardWorkdays,
                             BigDecimal overtimeDays, BigDecimal weekendDays, BigDecimal allowanceHours,
                             BigDecimal allowanceWorkdays, int lateDayCount, int lateShiftCount, int absenceDayCount,
                             boolean hasInvalidCode) {}

    public LineHours aggregate(List<TimesheetEntry> entries, BigDecimal standardHoursPerDay,
            Set<UUID> absenceAttendanceCodeIds) {
        BigDecimal totalHours = sum(entries, TimesheetEntry::getPaidHours);
        BigDecimal standardHours = entries.stream()
                .filter(e -> !e.isWeekend())
                .map(e -> e.getPaidHours().min(standardHoursPerDay))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overtimeHours = entries.stream()
                .map(e -> e.getPaidHours().subtract(standardHoursPerDay).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weekendHours = entries.stream()
                .filter(TimesheetEntry::isWeekend)
                .map(TimesheetEntry::getPaidHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal regularHours = totalHours.subtract(overtimeHours);
        BigDecimal allowanceHours = sum(entries, TimesheetEntry::getAllowanceHours);

        int lateDayCount = (int) entries.stream().filter(TimesheetEntry::isLate).count();
        int lateShiftCount = entries.stream().mapToInt(TimesheetEntry::getLateShifts).sum();
        int absenceDayCount = (int) entries.stream()
                .filter(e -> e.getAttendanceCodeId() != null && absenceAttendanceCodeIds.contains(e.getAttendanceCodeId()))
                .count();
        boolean hasInvalidCode = entries.stream().anyMatch(TimesheetEntry::isInvalid);

        return new LineHours(totalHours, standardHours, overtimeHours, weekendHours, regularHours,
                divide(regularHours, standardHoursPerDay), divide(overtimeHours, standardHoursPerDay),
                divide(weekendHours, standardHoursPerDay), allowanceHours, divide(allowanceHours, standardHoursPerDay),
                lateDayCount, lateShiftCount, absenceDayCount, hasInvalidCode);
    }

    private BigDecimal sum(List<TimesheetEntry> entries, java.util.function.Function<TimesheetEntry, BigDecimal> field) {
        return entries.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal divide(BigDecimal value, BigDecimal standardHoursPerDay) {
        if (standardHoursPerDay.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(standardHoursPerDay, 6, java.math.RoundingMode.HALF_UP);
    }
}

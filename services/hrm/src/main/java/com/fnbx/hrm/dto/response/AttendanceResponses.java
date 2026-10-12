package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AttendanceResponses {

    private AttendanceResponses() {}

    public record ShiftLine(UUID shiftAssignmentId, String slotName, LocalTime startTime, LocalTime endTime,
                            BigDecimal scheduledHours, BigDecimal payableHours, String status, String lateLevel, String note,
                            boolean excused, BigDecimal adjustedHours) {}

    public record DayCell(LocalDate date, BigDecimal scheduledHours, BigDecimal payableHours, int lateShifts, String color,
                          List<ShiftLine> shifts) {}

    public record Row(UUID staffId, String nickname, String employmentType, List<DayCell> days, BigDecimal totalPayableHours,
                      int totalLateShifts) {}

    public record Sheet(UUID attendanceSheetId, UUID shiftScheduleId, UUID branchId, LocalDate weekStart, String status,
                        long version, Instant submittedAt, Instant confirmedAt, String returnReason, List<Row> rows,
                        Integer skippedCells) {

        public Sheet withSkippedCells(int skipped) {
            return new Sheet(attendanceSheetId, shiftScheduleId, branchId, weekStart, status, version, submittedAt, confirmedAt,
                    returnReason, rows, skipped);
        }
    }

    public record PayrollCell(UUID staffId, String nickname, LocalDate date, String rawValue, int lateShifts, UUID periodId,
                              String periodStatus) {}
}

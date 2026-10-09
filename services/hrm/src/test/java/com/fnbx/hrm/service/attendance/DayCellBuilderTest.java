package com.fnbx.hrm.service.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.LateLevel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DayCellBuilderTest {

    private final DayCellBuilder builder = new DayCellBuilder();

    private static ShiftOutcome onTime(String hours) {
        return new ShiftOutcome(new BigDecimal(hours), null, null);
    }

    private static ShiftOutcome with(String hours, AttendanceExceptionStatus status, LateLevel level) {
        return new ShiftOutcome(new BigDecimal(hours), status, level);
    }

    @Test
    void onTimeShiftsAreSummedWithDecimalComma() {
        TimesheetDayCell cell = builder.build(List.of(onTime("4"), onTime("1.5")));

        assertEquals("5,5", cell.rawValue());
        assertEquals(0, cell.lateShifts());
    }

    @Test
    void singleLateShiftLevelOneUsesTheHalfShiftCode() {
        TimesheetDayCell cell = builder.build(List.of(with("8", AttendanceExceptionStatus.LATE, LateLevel.LEVEL_1)));

        assertEquals("T2-8", cell.rawValue());
        assertEquals(1, cell.lateShifts());
        assertEquals(0, new BigDecimal("4.00").compareTo(cell.payableHours()));
    }

    @Test
    void singleLateShiftLevelTwoUsesTheVoidCodeAndStillCountsAsLate() {
        TimesheetDayCell cell = builder.build(List.of(with("8", AttendanceExceptionStatus.LATE, LateLevel.LEVEL_2)));

        assertEquals("T3-8", cell.rawValue());
        assertEquals(1, cell.lateShifts());
        assertEquals(0, BigDecimal.ZERO.compareTo(cell.payableHours()));
    }

    @Test
    void lateShiftAmongSeveralIsWrittenAsHours() {
        TimesheetDayCell cell = builder.build(List.of(onTime("4"), with("4", AttendanceExceptionStatus.LATE, LateLevel.LEVEL_1)));

        assertEquals("6", cell.rawValue());
        assertEquals(1, cell.lateShifts());
    }

    @Test
    void leaveMapsToItsCodes() {
        assertEquals("CP", builder.build(List.of(with("8", AttendanceExceptionStatus.LEAVE_PAID, null))).rawValue());
        assertEquals("KL", builder.build(List.of(with("8", AttendanceExceptionStatus.LEAVE_UNPAID, null))).rawValue());
    }

    @Test
    void absentDayWritesNoCell() {
        assertNull(builder.build(List.of(with("4", AttendanceExceptionStatus.ABSENT, null))).rawValue());
    }
}

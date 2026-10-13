package com.fnbx.hrm.service.attendance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fnbx.hrm.dto.request.AttendanceRequests.Mark;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.AttendanceExceptionStatus;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.LateLevel;
import com.fnbx.hrm.service.scheduling.SchedulingStaff;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceMarkValidatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 10);

    private final AttendanceMarkValidator validator = new AttendanceMarkValidator();
    private final SchedulingStaff fullTimer = staff(EmploymentType.FULLTIME);
    private final SchedulingStaff partTimer = staff(EmploymentType.PARTTIME);

    private SchedulingStaff staff(EmploymentType type) {
        EmploymentAssignment contract = new EmploymentAssignment();
        contract.setEmploymentType(type);
        contract.setEffectiveFrom(DAY.minusYears(1));
        return new SchedulingStaff(UUID.randomUUID(), "Nick", "First", "First Last", Set.of(), List.of(contract), null, false);
    }

    private Mark mark(AttendanceExceptionStatus status, LateLevel level, String note, Boolean excused, BigDecimal hours) {
        return new Mark(status, level, note, excused, hours);
    }

    @Test
    void lateNeedsALevelUnlessItIsExcused() {
        assertDoesNotThrow(() -> validator.validate(mark(AttendanceExceptionStatus.LATE, LateLevel.LEVEL_0, null, null, null), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.LATE, null, "x", null, null), fullTimer, DAY));
    }

    @Test
    void excusedLateNeedsANoteAndHasNoLevel() {
        assertDoesNotThrow(() -> validator.validate(mark(AttendanceExceptionStatus.LATE, null, "SM allowed", true, null), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.LATE, null, " ", true, null), fullTimer, DAY));
        assertThrows(RuntimeException.class,
                () -> validator.validate(mark(AttendanceExceptionStatus.LATE, LateLevel.LEVEL_0, "x", true, null), fullTimer, DAY));
    }

    @Test
    void onlyALateShiftCanBeExcused() {
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.ABSENT, null, "x", true, null), fullTimer, DAY));
    }

    @Test
    void adjustedNeedsHoursWithinADayAndANote() {
        assertDoesNotThrow(() -> validator.validate(mark(AttendanceExceptionStatus.ADJUSTED, null, "left early", null, new BigDecimal("5.5")), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.ADJUSTED, null, "x", null, null), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.ADJUSTED, null, null, null, BigDecimal.ONE), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.ADJUSTED, null, "x", null, new BigDecimal("25")), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.ADJUSTED, null, "x", null, new BigDecimal("-1")), fullTimer, DAY));
        assertThrows(RuntimeException.class,
                () -> validator.validate(mark(AttendanceExceptionStatus.LATE, LateLevel.LEVEL_0, "x", null, BigDecimal.ONE), fullTimer, DAY));
    }

    @Test
    void annualLeaveIsForFullTimersAndUnpaidLeaveIsGone() {
        assertDoesNotThrow(() -> validator.validate(mark(AttendanceExceptionStatus.LEAVE_PAID, null, null, null, null), fullTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.LEAVE_PAID, null, null, null, null), partTimer, DAY));
        assertThrows(RuntimeException.class, () -> validator.validate(mark(AttendanceExceptionStatus.LEAVE_UNPAID, null, null, null, null), fullTimer, DAY));
    }
}

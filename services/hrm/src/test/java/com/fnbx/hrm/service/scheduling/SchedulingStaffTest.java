package com.fnbx.hrm.service.scheduling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchedulingStaffTest {

    private final UUID thuDuc = UUID.randomUUID();
    private final UUID tuXuong = UUID.randomUUID();

    private SchedulingStaff person(UUID home, boolean manager) {
        return new SchedulingStaff(UUID.randomUUID(), "Nick", "First", "First Last", Set.of(), List.of(), home, manager);
    }

    @Test
    void weeksNobodyCouldMeanAreRefused() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 11);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> WeekCalendar.requireReasonable(java.time.LocalDate.of(2025, 12, 29), today));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> WeekCalendar.requireReasonable(java.time.LocalDate.of(2026, 10, 26), today));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> WeekCalendar.requireReasonable(java.time.LocalDate.of(1999, 1, 4), today));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> WeekCalendar.requireReasonable(java.time.LocalDate.of(2099, 1, 5), today));
    }

    @Test
    void ownBranchOnly() {
        SchedulingStaff staff = person(thuDuc, false);

        assertTrue(staff.belongsTo(thuDuc));
        assertFalse(staff.belongsTo(tuXuong));
    }

    @Test
    void noDefaultBranchMeansAnywhere() {
        assertTrue(person(null, false).belongsTo(tuXuong));
    }

    @Test
    void managersCanBePlacedAnywhere() {
        assertTrue(person(thuDuc, true).belongsTo(tuXuong));
    }
}

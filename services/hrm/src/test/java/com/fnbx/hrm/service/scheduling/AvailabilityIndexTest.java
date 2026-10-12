package com.fnbx.hrm.service.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fnbx.hrm.service.scheduling.AvailabilityIndex.BusyWindow;
import com.fnbx.hrm.service.scheduling.AvailabilityIndex.WeekReport;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityIndexTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
    private static final TimeWindow MORNING = new TimeWindow(LocalTime.of(7, 0), LocalTime.of(11, 0));

    private final UUID manager = UUID.randomUUID();
    private final UUID employee = UUID.randomUUID();

    @Test
    void employeeWithoutRegistrationIsUnknown() {
        AvailabilityIndex index = new AvailabilityIndex(Map.of(), Set.of(manager));

        assertEquals(AvailabilityStatus.NO_SUBMISSION, index.statusOf(employee, DAY, MORNING));
    }

    @Test
    void managerWithoutRegistrationIsFree() {
        AvailabilityIndex index = new AvailabilityIndex(Map.of(), Set.of(manager));

        assertEquals(AvailabilityStatus.FREE, index.statusOf(manager, DAY, MORNING));
    }

    @Test
    void managerBusyShiftsStillCountWhenRegistered() {
        AvailabilityIndex index = new AvailabilityIndex(
                Map.of(manager, new WeekReport(true, List.of(new BusyWindow(DAY, MORNING)))), Set.of(manager));

        assertEquals(AvailabilityStatus.BUSY, index.statusOf(manager, DAY, MORNING));
    }

    @Test
    void aLeaveDayBeatsEverythingElse() {
        WeekReport free = new WeekReport(true, List.of());
        AvailabilityIndex index = new AvailabilityIndex(Map.of(employee, free, manager, free), Set.of(manager),
                Map.of(employee, Set.of(DAY), manager, Set.of(DAY)));

        assertEquals(AvailabilityStatus.ON_LEAVE, index.statusOf(employee, DAY, MORNING));
        assertEquals(AvailabilityStatus.ON_LEAVE, index.statusOf(manager, DAY, MORNING));
        assertEquals(AvailabilityStatus.FREE, index.statusOf(employee, DAY.plusDays(1), MORNING));
    }
}

package com.fnbx.hrm.service.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Busy windows reported by each employee for one week; an employee who has not sent the registration is unknown, not free. */
public final class AvailabilityIndex {

    public record BusyWindow(LocalDate date, TimeWindow window) {}

    public record WeekReport(boolean submitted, List<BusyWindow> busy) {}

    private final Map<UUID, WeekReport> reports;

    AvailabilityIndex(Map<UUID, WeekReport> reports) {
        this.reports = reports;
    }

    public AvailabilityStatus statusOf(UUID staffId, LocalDate date, TimeWindow window) {
        WeekReport report = reports.get(staffId);
        if (report == null || !report.submitted()) {
            return AvailabilityStatus.NO_SUBMISSION;
        }
        boolean busy = report.busy().stream()
                .anyMatch(slot -> slot.date().equals(date) && slot.window().overlaps(window));
        return busy ? AvailabilityStatus.BUSY : AvailabilityStatus.FREE;
    }

    public boolean submitted(UUID staffId) {
        WeekReport report = reports.get(staffId);
        return report != null && report.submitted();
    }
}

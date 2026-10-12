package com.fnbx.hrm.service.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AvailabilityIndex {

    public record BusyWindow(LocalDate date, TimeWindow window) {}

    public record WeekReport(boolean submitted, List<BusyWindow> busy) {}

    private final Map<UUID, WeekReport> reports;
    private final Set<UUID> registrationOptional;
    private final Map<UUID, Set<LocalDate>> leaveDays;

    AvailabilityIndex(Map<UUID, WeekReport> reports) {
        this(reports, Set.of());
    }

    AvailabilityIndex(Map<UUID, WeekReport> reports, Set<UUID> registrationOptional) {
        this(reports, registrationOptional, Map.of());
    }

    AvailabilityIndex(Map<UUID, WeekReport> reports, Set<UUID> registrationOptional, Map<UUID, Set<LocalDate>> leaveDays) {
        this.reports = reports;
        this.registrationOptional = registrationOptional;
        this.leaveDays = leaveDays;
    }

    public AvailabilityStatus statusOf(UUID staffId, LocalDate date, TimeWindow window) {
        if (isOnLeave(staffId, date)) {
            return AvailabilityStatus.ON_LEAVE;
        }
        WeekReport report = reports.get(staffId);
        boolean registered = report != null && report.submitted();
        if (!registered && !registrationOptional.contains(staffId)) {
            return AvailabilityStatus.NO_SUBMISSION;
        }
        return isBusy(report, date, window) ? AvailabilityStatus.BUSY : AvailabilityStatus.FREE;
    }

    public boolean isOptional(UUID staffId) {
        return registrationOptional.contains(staffId);
    }

    public boolean submitted(UUID staffId) {
        WeekReport report = reports.get(staffId);
        return report != null && report.submitted();
    }

    private boolean isOnLeave(UUID staffId, LocalDate date) {
        return leaveDays.getOrDefault(staffId, Set.of()).contains(date);
    }

    private boolean isBusy(WeekReport report, LocalDate date, TimeWindow window) {
        return report != null && report.busy().stream()
                .anyMatch(busy -> busy.date().equals(date) && busy.window().overlaps(window));
    }
}

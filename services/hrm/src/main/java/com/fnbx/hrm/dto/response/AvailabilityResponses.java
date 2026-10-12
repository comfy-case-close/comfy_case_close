package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AvailabilityResponses {

    private AvailabilityResponses() {}

    public record Window(UUID branchId, LocalDate weekStart, String status, Instant openedAt, Instant closedAt) {}

    public record SlotChoice(UUID shiftSlotId, String name, LocalTime startTime, LocalTime endTime, boolean busy) {}

    public record DayForm(LocalDate date, List<SlotChoice> slots) {}

    /** What an employee sees when registering: the days of the week with the shifts of their own branch to mark busy. */
    public record OwnForm(LocalDate weekStart, UUID branchId, boolean open, String status, String note, Instant submittedAt,
                          long version, List<DayForm> days) {}

    public record BusyItem(LocalDate date, UUID shiftSlotId, String slotName, LocalTime startTime, LocalTime endTime) {}

    public record StaffRegistration(UUID staffId, String nickname, String fullName, String employmentType, String status,
                                    String note, Instant submittedAt, List<BusyItem> busyShifts) {}

    public record Overview(UUID branchId, LocalDate weekStart, String windowStatus, int total, int submitted, int draft,
                           int notSent, int busyShiftCount, List<StaffRegistration> staff) {}
}

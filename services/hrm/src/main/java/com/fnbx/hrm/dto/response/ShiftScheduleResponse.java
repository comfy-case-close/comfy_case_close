package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** The week grid of one branch: slots per day, who works them, and which time windows still lack someone. */
public record ShiftScheduleResponse(
        UUID shiftScheduleId,
        UUID branchId,
        LocalDate weekStart,
        String status,
        long version,
        Instant submittedAt,
        String submitNote,
        Instant approvedAt,
        String returnReason,
        List<DaySlots> days,
        List<AssignmentView> assignments,
        List<CoverageView> coverage,
        Stats stats) {

    public record DaySlots(LocalDate date, List<SlotView> slots) {}

    public record SlotView(UUID shiftSlotId, String name, String period, String employmentType,
                           LocalTime startTime, LocalTime endTime) {}

    public record AssignmentView(UUID shiftAssignmentId, UUID staffId, String nickname, String fullName, UUID positionId,
                                 String positionName, LocalDate date, UUID shiftSlotId, LocalTime startTime, LocalTime endTime,
                                 BigDecimal hours, String source, String availability, String note, long version) {}

    public record CoverageView(LocalDate date, String period, LocalTime startTime, LocalTime endTime,
                               boolean complete, List<NeedView> needs) {}

    public record NeedView(UUID positionId, String positionName, int required, int present) {}

    public record Stats(BigDecimal coverageRatio, int missingSeats, List<StaffHours> belowMinimum) {}

    public record StaffHours(UUID staffId, String nickname, BigDecimal hours, BigDecimal minimumHours) {}
}

package com.fnbx.hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleRequests {

    private ScheduleRequests() {}

    public record Create(@NotNull UUID branchId, @NotNull LocalDate weekStart, LocalDate copyFromWeekStart) {}

    public record Submit(String note, @NotNull Long expectedVersion) {}

    public record Approve(@NotNull Long expectedVersion) {}

    public record Return(@NotBlank String reason, @NotNull Long expectedVersion) {}

    /** {@code reason} is mandatory when the person is busy or has not registered for that shift. */
    public record AddAssignment(@NotNull UUID shiftScheduleId, @NotNull UUID staffId, @NotNull LocalDate date,
                                @NotNull UUID shiftSlotId, @NotNull UUID positionId, LocalTime startTime, LocalTime endTime,
                                String reason, String note) {}

    public record PatchAssignment(LocalTime startTime, LocalTime endTime, String note, @NotNull Long expectedVersion) {}

    public record Replace(@NotNull UUID newStaffId, String reason, @NotNull Long expectedVersion) {}

    public record Remove(@NotNull Long expectedVersion, String reason) {}

    public record AutoFill(boolean keepLastWeek) {}

    public enum BatchOperationType { ADD, REPLACE, REMOVE }

    public record BatchOperation(@NotNull BatchOperationType type, UUID assignmentId, @Valid AddAssignment add,
                                 @Valid Replace replace, @Valid Remove remove) {}

    public record Batch(@NotEmpty @Valid List<BatchOperation> operations) {}
}

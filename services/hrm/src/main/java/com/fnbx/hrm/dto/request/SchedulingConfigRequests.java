package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.ShiftDayType;
import com.fnbx.hrm.enums.ShiftPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class SchedulingConfigRequests {

    private SchedulingConfigRequests() {}

    /** Creating a slot with the name of an existing one starts a new version from {@code effectiveFrom}. */
    public record ShiftSlot(@NotNull UUID branchId, @NotBlank String name, @NotNull ShiftPeriod period,
                            @NotNull EmploymentType employmentType, @NotNull ShiftDayType dayType,
                            @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull LocalDate effectiveFrom,
                            short sortOrder) {}

    public record Requirement(@NotNull ShiftPeriod period, @NotNull UUID positionId, @PositiveOrZero short minStaff) {}

    public record Requirements(@NotNull UUID branchId, @NotNull LocalDate effectiveFrom, @NotEmpty @Valid List<Requirement> items) {}

    public record BranchSetting(@PositiveOrZero short minRestHours, @NotNull @PositiveOrZero BigDecimal minHoursPartTime,
                                @NotNull @PositiveOrZero BigDecimal minHoursFullTime, BigDecimal maxHoursWeek) {}

    public record StaffProfile(BigDecimal minHoursWeek, BigDecimal maxHoursWeek) {}
}

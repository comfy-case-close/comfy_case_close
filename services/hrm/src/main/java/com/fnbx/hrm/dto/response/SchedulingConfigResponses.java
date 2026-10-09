package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class SchedulingConfigResponses {

    private SchedulingConfigResponses() {}

    public record ShiftSlot(UUID shiftSlotId, UUID branchId, String name, String period, String employmentType,
                            String dayType, LocalTime startTime, LocalTime endTime, LocalDate effectiveFrom,
                            LocalDate effectiveTo, short sortOrder, boolean active) {}

    public record Requirement(String period, UUID positionId, short minStaff, LocalDate effectiveFrom) {}

    public record BranchSetting(UUID branchId, short minRestHours, BigDecimal minHoursPartTime, BigDecimal minHoursFullTime,
                                BigDecimal maxHoursWeek) {}

    public record StaffProfile(UUID staffId, BigDecimal minHoursWeek, BigDecimal maxHoursWeek) {}
}

package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Shifts and hours per person for the week; cost is present only for callers allowed to see labour cost. */
public record ScheduleSummaryTotalsResponse(List<PersonTotal> people, List<PositionTotal> positions) {

    public record PersonTotal(UUID staffId, String nickname, String employmentType, int shifts, BigDecimal hours,
                              BigDecimal minimumHours, BigDecimal estimatedCost) {}

    public record PositionTotal(UUID positionId, String positionName, int shifts, BigDecimal hours, BigDecimal estimatedCost) {}
}

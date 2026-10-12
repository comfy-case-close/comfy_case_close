package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.enums.ShiftPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Who is present in one time window of one shift period, against the minimum required per position. */
public record SegmentCoverage(LocalDate date, ShiftPeriod period, TimeWindow window, List<PositionNeed> needs) {

    public record PositionNeed(UUID positionId, int required, int present) {

        public boolean missing() {
            return present < required;
        }
    }

    public List<UUID> missingPositions() {
        return needs.stream().filter(PositionNeed::missing).map(PositionNeed::positionId).toList();
    }

    public boolean complete() {
        return missingPositions().isEmpty();
    }
}

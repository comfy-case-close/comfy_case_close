package com.fnbx.hrm.service.scheduling;

import com.fnbx.hrm.entity.ShiftAssignment;
import com.fnbx.hrm.entity.ShiftSlot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

public record TimeWindow(LocalTime start, LocalTime end) implements Comparable<TimeWindow> {

    public static TimeWindow of(ShiftSlot slot) {
        return new TimeWindow(slot.getStartTime(), slot.getEndTime());
    }

    public static TimeWindow of(ShiftAssignment assignment) {
        return new TimeWindow(assignment.getStartTime(), assignment.getEndTime());
    }

    public boolean covers(TimeWindow other) {
        return !start.isAfter(other.start) && !end.isBefore(other.end);
    }

    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public int minutes() {
        return (int) Duration.between(start, end).toMinutes();
    }

    public BigDecimal hours() {
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    @Override
    public int compareTo(TimeWindow other) {
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }
}

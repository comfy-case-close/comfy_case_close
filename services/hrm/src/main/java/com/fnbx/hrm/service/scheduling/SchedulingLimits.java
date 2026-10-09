package com.fnbx.hrm.service.scheduling;

import java.math.BigDecimal;

public record SchedulingLimits(int minRestHours, BigDecimal minHoursPartTime, BigDecimal minHoursFullTime, BigDecimal maxHoursWeek) {

    public static final SchedulingLimits DEFAULT =
            new SchedulingLimits(10, BigDecimal.valueOf(20), BigDecimal.valueOf(48), null);
}

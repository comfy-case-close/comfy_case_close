package com.fnbx.hrm.service.scheduling.autofill;

import com.fnbx.hrm.service.scheduling.TimeWindow;
import com.fnbx.hrm.enums.ShiftPeriod;
import java.time.LocalDate;
import java.util.UUID;

/** One person working one shift, whether it is already on the schedule or proposed by the auto-fill. */
public record Placed(UUID staffId, LocalDate date, UUID shiftSlotId, String slotName, ShiftPeriod period,
                     UUID positionId, TimeWindow window) {
}

package com.fnbx.hrm.service.scheduling.autofill;

import com.fnbx.hrm.enums.ShiftPeriod;
import com.fnbx.hrm.service.scheduling.TimeWindow;
import java.time.LocalDate;
import java.util.UUID;

public record UnfilledGap(LocalDate date, ShiftPeriod period, TimeWindow window, UUID positionId, Reason reason) {

    public enum Reason {
        NO_QUALIFIED_STAFF,
        ALL_BUSY,
        NO_SUBMISSION,
        OTHER_BRANCH,
        NO_FREE_SLOT
    }
}

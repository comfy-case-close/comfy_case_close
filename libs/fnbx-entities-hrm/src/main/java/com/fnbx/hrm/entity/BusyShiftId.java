package com.fnbx.hrm.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class BusyShiftId implements Serializable {

    private UUID availabilitySubmissionId;
    private LocalDate workDate;
    private UUID shiftSlotId;
}

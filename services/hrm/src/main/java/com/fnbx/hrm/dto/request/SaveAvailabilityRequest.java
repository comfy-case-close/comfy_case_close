package com.fnbx.hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The registration is the list of shifts the employee is busy; every other shift is free. */
public record SaveAvailabilityRequest(@NotNull @Valid List<BusyShiftInput> busyShifts, @Size(max = 500) String note,
                                      Long expectedVersion) {

    public record BusyShiftInput(@NotNull LocalDate date, @NotNull UUID shiftSlotId) {}
}

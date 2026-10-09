package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdatePeriodDatesRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotNull Long expectedVersion) {
}

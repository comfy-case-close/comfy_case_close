package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.TerminationReason;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TerminateEmployeeRequest(@NotNull LocalDate terminatedOn, @NotNull TerminationReason reasonCode, String note) {
}

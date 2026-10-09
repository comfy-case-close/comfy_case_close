package com.fnbx.hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateEmployeeRequest(@NotNull @Valid EmployeeProfileInput profile, @NotNull Long expectedVersion) {
}

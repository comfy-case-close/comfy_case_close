package com.fnbx.hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code staffId} must already exist in {@code identity.staff} - hrm never creates login identities. */
public record CreateEmployeeRequest(@NotNull UUID staffId, @Valid EmployeeProfileInput profile) {
}

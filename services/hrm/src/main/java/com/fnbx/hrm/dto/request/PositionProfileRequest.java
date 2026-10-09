package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Upserts the payroll profile of an existing {@code identity.staff_position} row. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionProfileRequest {
    @NotNull
    private UUID departmentId;
    private boolean trainee;
}

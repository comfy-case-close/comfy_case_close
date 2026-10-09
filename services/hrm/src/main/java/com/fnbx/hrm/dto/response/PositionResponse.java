package com.fnbx.hrm.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code identity.staff_position} joined with its payroll profile, if one exists. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionResponse {
    private UUID positionId;
    private String positionCode;
    private String positionName;
    private boolean active;
    private UUID departmentId;
    private String departmentName;
    private boolean trainee;
}

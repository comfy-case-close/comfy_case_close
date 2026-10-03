package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EmploymentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceInputPreviewRequest {
    private String rawValue;
    @NotNull
    private EmploymentType employmentType;
}

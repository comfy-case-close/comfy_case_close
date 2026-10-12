package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRequest {
    @NotBlank
    private String departmentCode;
    @NotBlank
    private String departmentName;
    private short displayOrder;
    private boolean active = true;
}

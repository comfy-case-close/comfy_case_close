package com.fnbx.hrm.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentResponse {
    private UUID departmentId;
    private String departmentCode;
    private String departmentName;
    private short displayOrder;
    private boolean active;
}

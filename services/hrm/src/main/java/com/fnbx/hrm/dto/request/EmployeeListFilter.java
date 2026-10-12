package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EmploymentType;
import java.util.UUID;
import lombok.Data;

@Data
public class EmployeeListFilter {
    private String search;
    private UUID branchId;
    private EmploymentType employmentType;
    private Boolean active;
}

package com.fnbx.hrm.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Which person is placed where in a payroll period. Never carries pay figures, so branch managers may see it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollLineSummaryResponse {
    private UUID payrollLineId;
    private UUID payrollPeriodId;
    private UUID assignmentId;
    private UUID staffId;
    private String employeeCode;
    private String employeeName;
    private UUID positionId;
    private UUID branchId;
    private String employmentType;
    private String note;
    private long version;
}

package com.fnbx.hrm.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetRowResponse {
    private UUID payrollLineId;
    private UUID staffId;
    private String employeeCode;
    private String employeeName;
    private UUID branchId;
    private long version;
    private List<TimesheetCellResponse> cells;
    private PayrollLineHourTotalsResponse totals;
}

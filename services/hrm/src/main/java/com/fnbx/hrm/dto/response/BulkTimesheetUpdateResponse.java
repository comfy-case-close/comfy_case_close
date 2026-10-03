package com.fnbx.hrm.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkTimesheetUpdateResponse {
    private List<TimesheetCellResponse> saved;
    private List<PayrollLineHourTotalsResponse> rowTotals;
    private boolean calculationStale;
}

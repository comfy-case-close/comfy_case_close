package com.fnbx.hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkTimesheetUpdateRequest {
    @NotEmpty
    @Valid
    private List<TimesheetCellUpdateRequest> cells;
}

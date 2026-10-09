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
public class TimesheetGridResponse {
    private UUID periodId;
    private String employmentType;
    private List<TimesheetDayResponse> days;
    private List<TimesheetRowResponse> rows;
}

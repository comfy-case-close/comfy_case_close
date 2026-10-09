package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetDayResponse {
    private LocalDate date;
    private int isoDow;
    private boolean weekend;
}

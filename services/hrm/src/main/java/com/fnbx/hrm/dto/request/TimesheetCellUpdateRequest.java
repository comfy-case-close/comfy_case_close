package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetCellUpdateRequest {
    @NotNull
    private UUID lineId;
    @NotNull
    private LocalDate date;
    private String raw;
    @NotNull
    private Long expectedVersion;
}

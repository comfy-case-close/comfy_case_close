package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndEmploymentAssignmentRequest {
    @NotNull
    private LocalDate effectiveTo;
}

package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCodeRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String description;
    @NotNull
    private BigDecimal dayCredit;
    private boolean paid;
    private boolean consumesAnnualLeave;
    private boolean countsAsAbsence;
    private boolean fulltimeOnly;
}

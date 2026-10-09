package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceCodeResponse {
    private UUID attendanceCodeId;
    private String code;
    private String description;
    private BigDecimal dayCredit;
    private boolean paid;
    private boolean consumesAnnualLeave;
    private boolean countsAsAbsence;
    private boolean fulltimeOnly;
}

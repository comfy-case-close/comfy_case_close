package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceInputPreviewResponse {
    private BigDecimal paidHours;
    private BigDecimal allowanceHours;
    private boolean invalid;
    private String message;
}

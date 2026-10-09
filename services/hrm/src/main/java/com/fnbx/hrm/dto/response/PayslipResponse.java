package com.fnbx.hrm.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayslipResponse {
    private UUID payslipId;
    private UUID periodId;
    private UUID staffId;
    private String employeeCode;
    private String employeeName;
    private short payrollLineCount;
    private BigDecimal grossTotal;
    private BigDecimal employeeInsuranceTotal;
    private BigDecimal advanceTotal;
    private BigDecimal deductionTotal;
    private BigDecimal netTotal;
    private short lateDayTotal;
    private String confirmationStatus;
    private java.time.Instant confirmedAt;
    /** Late shifts across the period, counted per shift and not per day. */
    private short lateShiftTotal;
    private BigDecimal paidLeaveDaysUsed;
    private BigDecimal unpaidLeaveDays;
    private BigDecimal annualLeaveRemaining;
    private Instant generatedAt;
    private String emailStatus;
}

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
public class PayrollLineResponse {
    private UUID payrollLineId;
    private UUID periodId;
    private UUID assignmentId;
    private UUID staffId;
    private String employeeCode;
    private String employeeName;
    private UUID positionId;
    private UUID branchId;
    private String employmentType;

    private BigDecimal totalHours;
    private BigDecimal standardHours;
    private BigDecimal overtimeHours;
    private BigDecimal weekendHours;
    private BigDecimal regularHours;
    private BigDecimal standardWorkdays;
    private BigDecimal allowanceHours;

    private BigDecimal grossPay;
    private BigDecimal employeeInsuranceTotal;
    private BigDecimal employerInsuranceTotal;
    private BigDecimal deductionTotal;
    private BigDecimal netPay;
    private BigDecimal laborCost;

    private short lateDayCount;
    private short lateShiftCount;
    private short absenceDayCount;
    private boolean hasInvalidCode;
    private Instant calculatedAt;
    private String note;
    private long version;
}

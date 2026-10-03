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
public class PayrollLineCalculationResponse {
    private UUID payrollLineId;
    private String employeeCode;
    private String employmentType;
    private UUID branchId;
    private boolean stale;
    private List<PayrollLineItemResponse> items;
    private List<PayrollLineInsuranceResponse> insurance;
    private PayrollLineResponse line;
}

package com.fnbx.hrm.service.salary;

import java.math.BigDecimal;
import java.util.List;

public record InsuranceBreakdown(List<SchemeAmount> schemes, BigDecimal employeeTotal, BigDecimal employerTotal) {

    public record SchemeAmount(String schemeCode, BigDecimal employeeAmount, BigDecimal employerAmount) {}
}

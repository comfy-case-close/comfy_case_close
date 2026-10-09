package com.fnbx.hrm.service.contract;

import java.math.BigDecimal;

public record ContractPay(BigDecimal monthlyBaseSalary, BigDecimal supplementAllowance, BigDecimal hourlyBaseRate) {
}

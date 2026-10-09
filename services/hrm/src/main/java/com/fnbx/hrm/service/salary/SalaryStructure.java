package com.fnbx.hrm.service.salary;

import java.math.BigDecimal;

public record SalaryStructure(BigDecimal baseSalary, BigDecimal supplementAllowance) {

    public BigDecimal fixedTotal() {
        return baseSalary.add(supplementAllowance);
    }
}

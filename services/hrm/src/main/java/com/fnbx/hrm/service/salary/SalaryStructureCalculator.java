package com.fnbx.hrm.service.salary;

import com.fnbx.hrm.exception.PayrollExceptions;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SalaryStructureCalculator {

    public SalaryStructure split(BigDecimal agreedMonthlySalary, BigDecimal insuranceBase, boolean insured) {
        if (!insured) {
            return new SalaryStructure(agreedMonthlySalary, BigDecimal.ZERO);
        }
        if (insuranceBase == null) {
            throw PayrollExceptions.invalidField("insuranceBase is required for an insured contract");
        }
        if (insuranceBase.compareTo(agreedMonthlySalary) > 0) {
            throw PayrollExceptions.insuranceBaseExceedsSalary();
        }
        return new SalaryStructure(insuranceBase, agreedMonthlySalary.subtract(insuranceBase));
    }
}

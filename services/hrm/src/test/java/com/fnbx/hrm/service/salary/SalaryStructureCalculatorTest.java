package com.fnbx.hrm.service.salary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SalaryStructureCalculatorTest {

    private final SalaryStructureCalculator calculator = new SalaryStructureCalculator();

    @Test
    void splitsAgreedSalaryIntoInsuranceBaseAndSupplement() {
        SalaryStructure structure = calculator.split(new BigDecimal("12000000"), new BigDecimal("5000000"), true);

        assertEquals(new BigDecimal("5000000"), structure.baseSalary());
        assertEquals(new BigDecimal("7000000"), structure.supplementAllowance());
        assertEquals(new BigDecimal("12000000"), structure.fixedTotal());
    }

    @Test
    void uninsuredContractKeepsTheWholeSalaryAsBase() {
        SalaryStructure structure = calculator.split(new BigDecimal("8000000"), null, false);

        assertEquals(new BigDecimal("8000000"), structure.baseSalary());
        assertEquals(BigDecimal.ZERO, structure.supplementAllowance());
    }

    @Test
    void rejectsInsuranceBaseAboveTheAgreedSalary() {
        AppException error = assertThrows(AppException.class,
                () -> calculator.split(new BigDecimal("4000000"), new BigDecimal("5000000"), true));

        assertEquals(ErrorCode.INSURANCE_BASE_EXCEEDS_SALARY, error.getErrorCode());
    }

    @Test
    void insuredContractNeedsAnInsuranceBase() {
        assertThrows(AppException.class, () -> calculator.split(new BigDecimal("8000000"), null, true));
    }
}

package com.fnbx.hrm.service.contract;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.service.salary.SalaryStructure;
import com.fnbx.hrm.service.salary.SalaryStructureCalculator;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractPayResolver {

    private final SalaryStructureCalculator calculator;

    public ContractPay resolve(EmploymentAssignmentRequest request) {
        if (request.getEmploymentType() == EmploymentType.PARTTIME) {
            return partTime(request);
        }
        return fullTime(request);
    }

    private ContractPay partTime(EmploymentAssignmentRequest request) {
        if (request.getHourlyBaseRate() == null) {
            throw PayrollExceptions.contractRateMissing();
        }
        return new ContractPay(null, BigDecimal.ZERO, request.getHourlyBaseRate());
    }

    private ContractPay fullTime(EmploymentAssignmentRequest request) {
        boolean agreedForm = request.getAgreedMonthlySalary() != null;
        boolean directForm = request.getMonthlyBaseSalary() != null;
        if (agreedForm && directForm) {
            throw PayrollExceptions.invalidField("Send either agreedMonthlySalary or monthlyBaseSalary, not both");
        }
        if (agreedForm) {
            SalaryStructure structure = calculator.split(
                    request.getAgreedMonthlySalary(), request.getInsuranceBase(), request.isInsured());
            return new ContractPay(structure.baseSalary(), structure.supplementAllowance(), null);
        }
        if (directForm) {
            BigDecimal supplement = request.getSupplementAllowance() == null ? BigDecimal.ZERO : request.getSupplementAllowance();
            return new ContractPay(request.getMonthlyBaseSalary(), supplement, null);
        }
        throw PayrollExceptions.contractRateMissing();
    }
}

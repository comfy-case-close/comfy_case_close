package com.fnbx.hrm.service.salary;

import com.fnbx.hrm.entity.InsuranceScheme;
import com.fnbx.hrm.service.salary.InsuranceBreakdown.SchemeAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InsuranceEstimator {

    public InsuranceBreakdown estimate(BigDecimal insuranceBase, List<InsuranceScheme> schemes) {
        List<SchemeAmount> amounts = schemes.stream()
                .map(scheme -> new SchemeAmount(scheme.getSchemeCode(),
                        portion(insuranceBase, scheme.getEmployeeRate()),
                        portion(insuranceBase, scheme.getEmployerRate())))
                .toList();
        return new InsuranceBreakdown(amounts,
                sum(amounts.stream().map(SchemeAmount::employeeAmount).toList()),
                sum(amounts.stream().map(SchemeAmount::employerAmount).toList()));
    }

    private BigDecimal portion(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

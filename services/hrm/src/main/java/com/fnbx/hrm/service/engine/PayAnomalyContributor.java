package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lines whose result cannot be right: money owed by the employee, or hours worked without any pay. */
@Component
@RequiredArgsConstructor
public class PayAnomalyContributor implements PeriodIssueContributor {

    private final ValidationIssueFactory issueFactory;

    @Override
    public List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines) {
        List<DataValidationIssue> issues = new ArrayList<>();
        for (PayrollLine line : lines) {
            String ref = "line:" + line.getPayrollLineId();
            if (line.getNetPay() != null && line.getNetPay().signum() < 0) {
                issues.add(issueFactory.warning(period, IssueCode.NEGATIVE_NET_PAY, ref,
                        "Net pay is below zero: deductions are larger than what was earned. No transfer is created for this line."));
            }
            boolean worked = line.getTotalHours() != null && line.getTotalHours().signum() > 0;
            boolean paid = line.getGrossPay() != null && line.getGrossPay().signum() > 0;
            if (worked && !paid) {
                issues.add(issueFactory.warning(period, IssueCode.ZERO_PAY_WITH_HOURS, ref,
                        "Hours were worked but nothing is paid: check the contract's salary or hourly rate for its employment type"));
            }
        }
        return issues;
    }
}

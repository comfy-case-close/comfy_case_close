package com.fnbx.hrm.service.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.enums.IssueSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayAnomalyContributorTest {

    private final PayAnomalyContributor contributor = new PayAnomalyContributor(new ValidationIssueFactory());

    private static PayrollLine line(String hours, String gross, String net) {
        PayrollLine line = new PayrollLine();
        line.setPayrollLineId(UUID.randomUUID());
        line.setTotalHours(new BigDecimal(hours));
        line.setGrossPay(new BigDecimal(gross));
        line.setNetPay(new BigDecimal(net));
        return line;
    }

    private List<DataValidationIssue> run(PayrollLine... lines) {
        PayrollPeriod period = new PayrollPeriod();
        period.setPayrollPeriodId(UUID.randomUUID());
        period.setBusinessId(UUID.randomUUID());
        return contributor.issuesFor(period, null, List.of(lines));
    }

    @Test
    void warnsAboutANegativeNet() {
        List<DataValidationIssue> issues = run(line("0", "0", "-525000"));

        assertThat(issues).extracting(DataValidationIssue::getIssueCode).containsExactly(IssueCode.NEGATIVE_NET_PAY);
        assertThat(issues.get(0).getSeverity()).isEqualTo(IssueSeverity.WARNING);
    }

    @Test
    void warnsWhenHoursWereWorkedButNothingIsPaid() {
        List<DataValidationIssue> issues = run(line("195", "0", "0"));

        assertThat(issues).extracting(DataValidationIssue::getIssueCode).containsExactly(IssueCode.ZERO_PAY_WITH_HOURS);
    }

    @Test
    void staysQuietForNormalLinesAndForLinesWithNoHours() {
        assertThat(run(line("160", "8500000", "7600000"), line("0", "0", "0"))).isEmpty();
    }
}

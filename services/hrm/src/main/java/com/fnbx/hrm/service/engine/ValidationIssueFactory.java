package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.enums.IssueSeverity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ValidationIssueFactory {

    public DataValidationIssue warning(PayrollPeriod period, IssueCode code, String entityRef, String message) {
        DataValidationIssue issue = new DataValidationIssue();
        issue.setDataValidationIssueId(UUID.randomUUID());
        issue.setBusinessId(period.getBusinessId());
        issue.setPeriodId(period.getPayrollPeriodId());
        issue.setIssueCode(code);
        issue.setSeverity(IssueSeverity.WARNING);
        issue.setEntityRef(entityRef);
        issue.setMessage(message);
        issue.setDetectedAt(Instant.now());
        return issue;
    }
}

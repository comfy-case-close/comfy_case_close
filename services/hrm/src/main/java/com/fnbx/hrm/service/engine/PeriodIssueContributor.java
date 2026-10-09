package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import java.util.List;

/** One independent source of validation issues for a payroll run. */
public interface PeriodIssueContributor {

    List<DataValidationIssue> issuesFor(PayrollPeriod period, PayrollConfig config, List<PayrollLine> lines);
}

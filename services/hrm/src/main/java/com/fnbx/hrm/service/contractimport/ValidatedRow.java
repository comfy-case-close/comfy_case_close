package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.dto.request.EmployeeProfileInput;
import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.enums.ImportRowStatus;
import java.util.List;
import java.util.UUID;

public record ValidatedRow(
        int rowNo,
        UUID staffId,
        EmployeeProfileInput profile,
        EmploymentAssignmentRequest assignment,
        List<ImportIssue> issues) {

    public ImportRowStatus status() {
        if (issues.stream().anyMatch(issue -> issue.severity() == ImportIssue.Severity.ERROR)) {
            return ImportRowStatus.ERROR;
        }
        return issues.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.WARNING;
    }

    public boolean committable() {
        return status() != ImportRowStatus.ERROR;
    }
}

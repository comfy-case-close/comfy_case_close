package com.fnbx.hrm.dto.response;

import java.util.List;
import java.util.UUID;

public record ImportJobResponse(
        UUID importJobId,
        String fileName,
        String mode,
        String status,
        Counts counts,
        List<ImportRowResponse> rows) {

    public record Counts(int total, int valid, int warning, int error, int committed) {}

    public record ImportRowResponse(int rowNo, String status, String employeeCode,
                                    List<IssueResponse> issues, UUID employmentAssignmentId) {}

    public record IssueResponse(String severity, String code, String message) {}
}

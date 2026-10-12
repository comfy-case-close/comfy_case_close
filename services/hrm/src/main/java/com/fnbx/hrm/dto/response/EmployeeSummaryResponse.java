package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeSummaryResponse(
        UUID staffId,
        String employeeCode,
        String nickname,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        LocalDate hiredOn,
        LocalDate terminatedOn,
        boolean active,
        UUID positionId,
        UUID defaultBranchId,
        String employmentType,
        String jobLevel,
        String contractKind,
        LocalDate contractEndsOn,
        boolean contractExpiringSoon,
        boolean birthdayThisMonth,
        int completenessPercent) {
}

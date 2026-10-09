package com.fnbx.hrm.dto.response;

public record EmployeeOverviewResponse(long activeEmployees, long contractsExpiringSoon,
                                       long birthdaysThisMonth, long incompleteProfiles) {
}

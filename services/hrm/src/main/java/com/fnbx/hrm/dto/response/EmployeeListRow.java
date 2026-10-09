package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeListRow(
        UUID staffId,
        String employeeCode,
        String nickname,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        LocalDate hiredOn,
        LocalDate terminatedOn,
        boolean active) {
}

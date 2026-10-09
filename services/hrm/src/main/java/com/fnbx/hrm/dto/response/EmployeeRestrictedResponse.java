package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Branch-manager view: never carries bank details, salary or any other money field. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRestrictedResponse {
    private UUID staffId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private LocalDate hiredOn;
    private boolean active;
}

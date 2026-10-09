package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.EducationGrade;
import com.fnbx.hrm.enums.EducationLevel;
import com.fnbx.hrm.enums.Gender;
import com.fnbx.hrm.enums.MaritalStatus;
import com.fnbx.hrm.enums.RecruitmentSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Every field is optional; a field that is absent leaves the stored value unchanged. */
public record EmployeeProfileInput(
        LocalDate dateOfBirth,
        Gender gender,
        MaritalStatus maritalStatus,
        @Min(0) @Max(20) Short childrenCount,
        @Size(max = 60) String ethnicity,
        @Size(max = 60) String religion,
        @Size(min = 2, max = 2) String nationality,
        @Size(max = 20) String nationalIdNo,
        LocalDate nationalIdIssuedOn,
        @Size(max = 120) String nationalIdIssuedPlace,
        @Size(max = 20) String socialInsuranceNo,
        @Size(max = 20) String taxCode,
        EducationLevel educationLevel,
        @Size(max = 160) String educationSchool,
        @Size(max = 160) String educationMajor,
        Short graduationYear,
        EducationGrade educationGrade,
        @Email String personalEmail,
        @Size(max = 200) String addressStreet,
        @Size(max = 120) String addressWard,
        String addressProvinceCode,
        RecruitmentSource recruitmentSource,
        String bankCode,
        @Size(max = 40) String bankAccountNo,
        @Size(max = 120) String bankAccountName,
        LocalDate hiredOn,
        String note) {
}

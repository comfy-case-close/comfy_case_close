package com.fnbx.hrm.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Identity numbers and the bank account are always masked; the contract print path is the only place they leave in full. */
@Builder
public record EmployeeResponse(
        UUID staffId,
        String employeeCode,
        String nickname,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String maritalStatus,
        Short childrenCount,
        String ethnicity,
        String religion,
        String nationality,
        String nationalIdMasked,
        LocalDate nationalIdIssuedOn,
        String nationalIdIssuedPlace,
        String socialInsuranceMasked,
        String taxCodeMasked,
        String educationLevel,
        String educationSchool,
        String educationMajor,
        Short graduationYear,
        String educationGrade,
        String personalEmail,
        String addressStreet,
        String addressWard,
        String addressProvinceCode,
        String recruitmentSource,
        String bankCode,
        String bankAccountMasked,
        String bankAccountName,
        LocalDate hiredOn,
        LocalDate terminatedOn,
        String terminationReason,
        boolean active,
        String note,
        long version,
        int completenessPercent,
        List<String> missingFields) {
}

package com.fnbx.hrm.mapper;

import com.fnbx.hrm.dto.response.AnnualLeaveQuotaResponse;
import com.fnbx.hrm.dto.response.EmployeeAllowanceResponse;
import com.fnbx.hrm.dto.response.EmployeeResponse;
import com.fnbx.hrm.dto.response.EmployeeRestrictedResponse;
import com.fnbx.hrm.dto.response.EmploymentAssignmentResponse;
import com.fnbx.hrm.entity.EmployeeAllowance;
import com.fnbx.hrm.entity.EmployeeLeaveQuota;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.service.employee.ProfileCompleteness;
import com.fnbx.hrm.service.employee.SensitiveMask;
import com.fnbx.identity.entity.Staff;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EmployeeMapper {

    @Mapping(target = "staffId", source = "staff.staffId")
    @Mapping(target = "employeeCode", source = "staff.employeeCode")
    @Mapping(target = "nickname", source = "staff.nickname")
    @Mapping(target = "firstName", source = "staff.firstName")
    @Mapping(target = "lastName", source = "staff.lastName")
    @Mapping(target = "email", source = "staff.email")
    @Mapping(target = "phone", source = "staff.phone")
    @Mapping(target = "nationalIdMasked", source = "profile.nationalIdNo", qualifiedByName = "mask")
    @Mapping(target = "socialInsuranceMasked", source = "profile.socialInsuranceNo", qualifiedByName = "mask")
    @Mapping(target = "taxCodeMasked", source = "profile.taxCode", qualifiedByName = "mask")
    @Mapping(target = "bankAccountMasked", source = "profile.bankAccountNo", qualifiedByName = "mask")
    @Mapping(target = "active", source = "profile.active")
    @Mapping(target = "note", source = "profile.note")
    @Mapping(target = "completenessPercent", source = "completeness.percent")
    @Mapping(target = "missingFields", source = "completeness.missingFields")
    EmployeeResponse toResponse(Staff staff, EmployeeProfile profile, ProfileCompleteness completeness);

    @Mapping(target = "staffId", source = "staff.staffId")
    @Mapping(target = "employeeCode", source = "staff.employeeCode")
    @Mapping(target = "firstName", source = "staff.firstName")
    @Mapping(target = "lastName", source = "staff.lastName")
    @Mapping(target = "hiredOn", source = "profile.hiredOn")
    @Mapping(target = "active", source = "profile.active")
    EmployeeRestrictedResponse toRestrictedResponse(Staff staff, EmployeeProfile profile);

    @Mapping(target = "fixedTotal", expression = "java(fixedTotal(assignment))")
    EmploymentAssignmentResponse toResponse(EmploymentAssignment assignment);

    EmployeeAllowanceResponse toResponse(EmployeeAllowance allowance);

    AnnualLeaveQuotaResponse toResponse(EmployeeLeaveQuota quota);

    @Named("mask")
    default String mask(String value) {
        return SensitiveMask.mask(value);
    }

    default BigDecimal fixedTotal(EmploymentAssignment assignment) {
        BigDecimal base = assignment.getMonthlyBaseSalary() == null ? BigDecimal.ZERO : assignment.getMonthlyBaseSalary();
        return base.add(assignment.getSupplementAllowance());
    }
}

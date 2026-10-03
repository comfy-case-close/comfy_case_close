package com.fnbx.hrm.mapper;

import com.fnbx.hrm.dto.response.AttendanceCodeResponse;
import com.fnbx.hrm.dto.response.DepartmentResponse;
import com.fnbx.hrm.dto.response.InsuranceSchemeResponse;
import com.fnbx.hrm.dto.response.LatePenaltyRuleResponse;
import com.fnbx.hrm.dto.response.PayComponentResponse;
import com.fnbx.hrm.dto.response.PayrollConfigResponse;
import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.Department;
import com.fnbx.hrm.entity.InsuranceScheme;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.entity.PayComponent;
import com.fnbx.hrm.entity.PayrollConfig;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PayrollSettingMapper {

    DepartmentResponse toResponse(Department department);

    AttendanceCodeResponse toResponse(AttendanceCode attendanceCode);

    LatePenaltyRuleResponse toResponse(LatePenaltyRule rule);

    InsuranceSchemeResponse toResponse(InsuranceScheme scheme);

    PayComponentResponse toResponse(PayComponent component);

    PayrollConfigResponse toResponse(PayrollConfig config);
}

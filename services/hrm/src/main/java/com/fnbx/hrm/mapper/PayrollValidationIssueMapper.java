package com.fnbx.hrm.mapper;

import com.fnbx.hrm.dto.response.PayrollValidationIssueResponse;
import com.fnbx.hrm.entity.DataValidationIssue;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PayrollValidationIssueMapper {

    PayrollValidationIssueResponse toResponse(DataValidationIssue issue);
}

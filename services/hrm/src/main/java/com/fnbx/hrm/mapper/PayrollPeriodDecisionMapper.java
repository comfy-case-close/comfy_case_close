package com.fnbx.hrm.mapper;

import com.fnbx.hrm.dto.response.PayrollPeriodDecisionResponse;
import com.fnbx.hrm.entity.PayrollPeriodDecision;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PayrollPeriodDecisionMapper {

    @org.mapstruct.Mapping(target = "actedByName", ignore = true)
    PayrollPeriodDecisionResponse toResponse(PayrollPeriodDecision decision);
}

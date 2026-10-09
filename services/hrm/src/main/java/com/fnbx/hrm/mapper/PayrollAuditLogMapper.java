package com.fnbx.hrm.mapper;

import com.fnbx.hrm.dto.response.PayrollAuditLogResponse;
import com.fnbx.hrm.entity.PayrollAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PayrollAuditLogMapper {

    PayrollAuditLogResponse toResponse(PayrollAuditLog log);
}

package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.PayrollAuditLogResponse;
import com.fnbx.hrm.mapper.PayrollAuditLogMapper;
import com.fnbx.hrm.repository.PayrollAuditLogRepository;
import com.fnbx.hrm.service.PayrollAuditLogService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollAuditLogServiceImpl implements PayrollAuditLogService {

    private final PayrollAuditLogRepository auditLogRepository;
    private final PayrollAuditLogMapper mapper;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public List<PayrollAuditLogResponse> searchAuditLogs(String entity, String entityId, Instant from, Instant to) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return auditLogRepository.searchAuditLogs(entity, entityId, from, to).stream().map(mapper::toResponse).toList();
    }
}

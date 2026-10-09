package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.PayrollAuditLogResponse;
import java.time.Instant;
import java.util.List;

public interface PayrollAuditLogService {

    List<PayrollAuditLogResponse> searchAuditLogs(String entity, String entityId, Instant from, Instant to);
}

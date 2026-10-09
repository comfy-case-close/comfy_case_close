package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.AcknowledgeValidationIssueRequest;
import com.fnbx.hrm.dto.response.PayrollValidationIssueResponse;
import java.util.List;
import java.util.UUID;

public interface PayrollValidationIssueService {

    List<PayrollValidationIssueResponse> list(UUID periodId);

    /** WARNING only - an ERROR must be fixed at the source, not waved through. */
    PayrollValidationIssueResponse acknowledge(UUID issueId, AcknowledgeValidationIssueRequest request);
}

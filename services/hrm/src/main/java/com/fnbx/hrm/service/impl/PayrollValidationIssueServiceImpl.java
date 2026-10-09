package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.AcknowledgeValidationIssueRequest;
import com.fnbx.hrm.dto.response.PayrollValidationIssueResponse;
import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.enums.IssueSeverity;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollValidationIssueMapper;
import com.fnbx.hrm.repository.DataValidationIssueRepository;
import com.fnbx.hrm.service.PayrollValidationIssueService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollValidationIssueServiceImpl implements PayrollValidationIssueService {

    private final DataValidationIssueRepository issueRepository;
    private final PayrollValidationIssueMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<PayrollValidationIssueResponse> list(UUID periodId) {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_APPROVE);
        return issueRepository.findByPeriodId(periodId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PayrollValidationIssueResponse acknowledge(UUID issueId, AcknowledgeValidationIssueRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_APPROVE);
        DataValidationIssue issue = issueRepository.findById(issueId).orElseThrow(PayrollExceptions::issueNotFound);
        if (issue.getSeverity() != IssueSeverity.WARNING) {
            throw PayrollExceptions.issueNotAcknowledgeable();
        }
        issue.setAcknowledged(true);
        issue.setAcknowledgedBy(TenantContext.current().userId());
        issue.setAcknowledgedAt(Instant.now());
        issue.setAcknowledgeNote(request.getNote());
        return mapper.toResponse(issueRepository.save(issue));
    }
}

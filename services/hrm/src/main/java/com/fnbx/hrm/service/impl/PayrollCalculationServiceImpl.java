package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.PayrollLineCalculationResponse;
import com.fnbx.hrm.dto.response.PayrollLineResponse;
import com.fnbx.hrm.dto.response.PayrollRunResponse;
import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.PayrollRun;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollValidationIssueMapper;
import com.fnbx.hrm.repository.DataValidationIssueRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.PayrollLineInsuranceRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayrollRunRepository;
import com.fnbx.hrm.service.PayrollCalculationService;
import com.fnbx.hrm.service.engine.PayrollEngine;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PayrollCalculationServiceImpl implements PayrollCalculationService {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollConfigRepository configRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollLineItemRepository itemRepository;
    private final PayrollLineInsuranceRepository insuranceRepository;
    private final DataValidationIssueRepository issueRepository;
    private final PayrollEngine payrollEngine;
    private final PayrollRunRecorder runRecorder;
    private final PayrollValidationIssueMapper issueMapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PayrollRunResponse runPayroll(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        PayrollPeriod period = requireDraftPeriod(periodId);
        PayrollConfig config = configRepository.findById(period.getConfigId())
                .orElseThrow(PayrollExceptions::resourceNotFound);

        PayrollRun run = runRecorder.startRun(period, config);
        PayrollEngine.EngineResult result = transactionTemplate.execute(status -> {
            PayrollEngine.EngineResult engineResult = payrollEngine.run(period, config);
            if (engineResult.hasError()) {
                status.setRollbackOnly();
            }
            return engineResult;
        });

        PayrollRun finished = runRecorder.finishRun(run.getPayrollRunId(), result);
        return toResponse(finished, result.issues());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollRunResponse> listRuns(UUID periodId) {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_APPROVE);
        return runRepository.findByPeriodIdOrderByStartedAtDesc(periodId).stream()
                .map(run -> toResponse(run, issueRepository.findByPeriodId(periodId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollRunResponse getRun(UUID runId) {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_APPROVE);
        PayrollRun run = runRepository.findById(runId).orElseThrow(PayrollExceptions::payrollRunNotFound);
        return toResponse(run, issueRepository.findByPeriodId(run.getPeriodId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollLineResponse> getPayrollTable(UUID periodId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return lineRepository.findLineResponses(periodId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollLineCalculationResponse getLineCalculation(UUID lineId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        PayrollLine line = lineRepository.findById(lineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
        PayrollLineResponse lineResponse = lineRepository.findLineResponses(line.getPeriodId(), null, null).stream()
                .filter(r -> r.getPayrollLineId().equals(lineId))
                .findFirst()
                .orElseThrow(PayrollExceptions::payrollLineNotFound);
        boolean stale = periodRepository.findById(line.getPeriodId())
                .map(p -> p.getTimesheetModifiedAt() != null
                        && (line.getCalculatedAt() == null || p.getTimesheetModifiedAt().isAfter(line.getCalculatedAt())))
                .orElse(false);

        return PayrollLineCalculationResponse.builder()
                .payrollLineId(lineId)
                .employeeCode(lineResponse.getEmployeeCode())
                .employmentType(lineResponse.getEmploymentType())
                .branchId(lineResponse.getBranchId())
                .stale(stale)
                .items(itemRepository.findItemResponses(lineId))
                .insurance(insuranceRepository.findInsuranceResponses(lineId))
                .line(lineResponse)
                .build();
    }

    private PayrollPeriod requireDraftPeriod(UUID periodId) {
        PayrollPeriod period = periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("Only a draft period can be run");
        }
        return period;
    }

    private PayrollRunResponse toResponse(PayrollRun run, List<DataValidationIssue> issues) {
        return PayrollRunResponse.builder()
                .payrollRunId(run.getPayrollRunId())
                .periodId(run.getPeriodId())
                .status(run.getStatus().name())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .linesCalculated(run.getLinesCalculated())
                .issues(issues.stream().map(issueMapper::toResponse).toList())
                .build();
    }
}

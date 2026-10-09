package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.PayrollRun;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.repository.DataValidationIssueRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollRunRepository;
import com.fnbx.hrm.service.engine.PayrollEngine;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a {@link PayrollRun}'s start and outcome in its own transactions,
 * separate from {@link com.fnbx.hrm.service.engine.PayrollEngine}'s
 * calculation transaction - so a failed run's own row and its issues survive
 * rolling back the figures that caused the failure (spec section 6.2 step 12).
 */
@Service
@RequiredArgsConstructor
public class PayrollRunRecorder {

    private final PayrollRunRepository runRepository;
    private final PayrollLineRepository lineRepository;
    private final DataValidationIssueRepository issueRepository;

    @Transactional
    public PayrollRun startRun(PayrollPeriod period, PayrollConfig config) {
        PayrollRun run = new PayrollRun();
        run.setPayrollRunId(UUID.randomUUID());
        run.setBusinessId(period.getBusinessId());
        run.setPeriodId(period.getPayrollPeriodId());
        run.setConfigId(config.getPayrollConfigId());
        run.setStatus(RunStatus.RUNNING);
        run.setTriggeredBy(TenantContext.current().userId());
        run.setStartedAt(Instant.now());
        return runRepository.save(run);
    }

    @Transactional
    public PayrollRun finishRun(UUID runId, PayrollEngine.EngineResult result) {
        PayrollRun run = runRepository.findById(runId).orElseThrow();

        issueRepository.deleteByPeriodId(run.getPeriodId());
        result.issues().forEach(issue -> issue.setBusinessId(run.getBusinessId()));
        issueRepository.saveAll(result.issues());

        run.setFinishedAt(Instant.now());
        if (result.hasError()) {
            run.setStatus(RunStatus.FAILED);
            run.setLinesCalculated(0);
        } else {
            run.setStatus(RunStatus.SUCCEEDED);
            run.setLinesCalculated(result.linesCalculated());
            lineRepository.findByPeriodId(run.getPeriodId()).forEach(line -> line.setCalcRunId(run.getPayrollRunId()));
        }
        return runRepository.save(run);
    }
}

package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.CreatePayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UnlockPayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UpdatePeriodDatesRequest;
import com.fnbx.hrm.dto.response.PayrollPeriodDecisionResponse;
import com.fnbx.hrm.dto.response.PayrollPeriodResponse;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.PayrollPeriodDecision;
import com.fnbx.hrm.entity.PayrollRun;
import com.fnbx.hrm.enums.PeriodDecisionAction;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.DataValidationIssueRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.mapper.PayrollPeriodDecisionMapper;
import com.fnbx.hrm.repository.PayrollPeriodDecisionRepository;
import com.fnbx.hrm.enums.ConfirmationStatus;
import com.fnbx.hrm.repository.PayrollPaymentRepository;
import com.fnbx.hrm.repository.PayslipConfirmationRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.service.payment.PaymentSynchronizer;
import com.fnbx.hrm.repository.PayrollRunRepository;
import com.fnbx.hrm.service.PayrollPeriodService;
import com.fnbx.hrm.service.PayrollLineService;
import com.fnbx.hrm.service.period.PeriodDateRange;
import com.fnbx.hrm.service.period.PeriodDateRangeResolver;
import com.fnbx.hrm.enums.IssueSeverity;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollPeriodServiceImpl implements PayrollPeriodService {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollPeriodDecisionRepository decisionRepository;
    private final PayrollPeriodDecisionMapper decisionMapper;
    private final PayrollConfigRepository payrollConfigRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final DataValidationIssueRepository issueRepository;
    private final PayrollLineService payrollLineService;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final PeriodDateRangeResolver dateRangeResolver;
    private final PayrollPaymentRepository paymentRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipConfirmationRepository confirmationRepository;
    private final PaymentSynchronizer paymentSynchronizer;

    @Override
    @Transactional(readOnly = true)
    public List<PayrollPeriodResponse> listPayrollPeriods(Short year, PeriodStatus status) {
        access.requireAnyBusinessOrAnyBranch(Permission.TIMESHEET_READ, Permission.PAYROLL_PROCESS,
                Permission.PAYROLL_APPROVE, Permission.PAYROLL_PAY, Permission.PAYSLIP_ISSUE, Permission.HR_RECORD_READ);
        return periodRepository.search(year, status == null ? null : status.name()).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public PayrollPeriodResponse createPayrollPeriod(CreatePayrollPeriodRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        short year = request.getPeriodYear();
        short month = request.getPeriodMonth();
        if (periodRepository.existsByPeriodYearAndPeriodMonth(year, month)) {
            throw PayrollExceptions.duplicatePeriod();
        }
        PayrollConfig config = payrollConfigRepository.findCurrent(LocalDate.now())
                .orElseThrow(PayrollExceptions::resourceNotFound);

        PeriodDateRange range = dateRangeResolver.resolve(year, month, request.getStartDate(), request.getEndDate(), config);

        PayrollPeriod period = new PayrollPeriod();
        period.setPayrollPeriodId(UUID.randomUUID());
        period.setBusinessId(TenantContext.current().businessId());
        period.setPeriodYear(year);
        period.setPeriodMonth(month);
        period.setConfigId(config.getPayrollConfigId());
        period.setStartDate(range.startDate());
        period.setEndDate(range.endDate());
        period.setStatus(PeriodStatus.DRAFT);
        PayrollPeriod saved = saveTranslatingOverlap(period);

        if (request.getCopyRosterFromPeriodId() != null) {
            payrollLineService.copyPayrollLines(saved.getPayrollPeriodId(), request.getCopyRosterFromPeriodId());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollPeriodResponse getPayrollPeriod(UUID periodId) {
        access.requireAnyBusinessOrAnyBranch(Permission.TIMESHEET_READ, Permission.PAYROLL_PROCESS,
                Permission.PAYROLL_APPROVE, Permission.PAYROLL_PAY, Permission.PAYSLIP_ISSUE, Permission.HR_RECORD_READ);
        return toResponse(requirePeriod(periodId));
    }

    @Override
    @Transactional
    public PayrollPeriodResponse updatePeriodDates(UUID periodId, UpdatePeriodDatesRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        PayrollPeriod period = requirePeriod(periodId);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("Only a draft period can change its dates");
        }
        if (period.getVersion() != request.expectedVersion()) {
            throw PayrollExceptions.versionConflict();
        }
        PeriodDateRange range = dateRangeResolver.validated(request.startDate(), request.endDate());
        period.setStartDate(range.startDate());
        period.setEndDate(range.endDate());
        period.setVersion(period.getVersion() + 1);
        return toResponse(saveTranslatingOverlap(period));
    }

    private PayrollPeriod saveTranslatingOverlap(PayrollPeriod period) {
        try {
            return periodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException ex) {
            throw PayrollExceptions.periodDatesOverlap();
        }
    }

    @Override
    @Transactional
    public PayrollPeriodResponse lockPeriod(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYROLL_APPROVE);
        PayrollPeriod period = requirePeriod(periodId);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("Only a draft period can be locked");
        }
        PayrollRun lastRun = payrollRunRepository.findFirstByPeriodIdOrderByStartedAtDesc(periodId)
                .orElseThrow(() -> PayrollExceptions.periodLocked("Run the payroll at least once before locking"));
        if (lastRun.getStatus() != RunStatus.SUCCEEDED) {
            throw PayrollExceptions.periodLocked("The last payroll run did not succeed");
        }
        if (isStale(period, lastRun)) {
            throw PayrollExceptions.staleCalculation();
        }
        if (issueRepository.countByPeriodIdAndSeverityAndAcknowledgedFalse(periodId, IssueSeverity.ERROR) > 0) {
            throw PayrollExceptions.periodLocked("Unresolved errors remain on this period");
        }
        if (issueRepository.countByPeriodIdAndSeverityAndAcknowledgedFalse(periodId, IssueSeverity.WARNING) > 0) {
            throw PayrollExceptions.unacknowledgedWarnings();
        }
        recordDecision(period, PeriodDecisionAction.LOCK, PeriodStatus.LOCKED, null);
        period.setLockedBy(TenantContext.current().userId());
        period.setLockedAt(java.time.Instant.now());
        PayrollPeriod locked = periodRepository.saveAndFlush(period);
        paymentSynchronizer.sync(locked);
        return toResponse(locked);
    }

    private boolean isStale(PayrollPeriod period, PayrollRun lastRun) {
        return period.getTimesheetModifiedAt() != null
                && lastRun.getFinishedAt() != null
                && period.getTimesheetModifiedAt().isAfter(lastRun.getFinishedAt());
    }

    @Override
    @Transactional
    public PayrollPeriodResponse unlockPeriod(UUID periodId, UnlockPayrollPeriodRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_APPROVE);
        PayrollPeriod period = requirePeriod(periodId);
        if (period.getStatus() != PeriodStatus.LOCKED) {
            throw PayrollExceptions.periodNotDraft("Only a locked period can be unlocked");
        }
        if (paymentRepository.existsPaidInPeriod(periodId)) {
            throw PayrollExceptions.periodHasPaidPayments();
        }
        recordDecision(period, PeriodDecisionAction.UNLOCK, PeriodStatus.DRAFT, request.getReason());
        period.setLockedBy(null);
        period.setLockedAt(null);
        return toResponse(periodRepository.saveAndFlush(period));
    }

    @Override
    @Transactional
    public PayrollPeriodResponse markPayrollPeriodPaid(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYROLL_APPROVE);
        PayrollPeriod period = requirePeriod(periodId);
        if (period.getStatus() != PeriodStatus.LOCKED) {
            throw PayrollExceptions.periodNotDraft("Only a locked period can be marked paid");
        }
        if (paymentRepository.existsUnpaidInPeriod(periodId)) {
            throw PayrollExceptions.periodHasOpenPayments();
        }
        recordDecision(period, PeriodDecisionAction.MARK_PAID, PeriodStatus.PAID, null);
        return toResponse(periodRepository.saveAndFlush(period));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollPeriodDecisionResponse> listDecisions(UUID periodId) {
        access.requireAnyBusiness(Permission.PAYROLL_APPROVE, Permission.HR_RECORD_READ);
        requirePeriod(periodId);
        return decisionRepository.findByPeriodIdOrderByActedAtAsc(periodId).stream()
                .map(decisionMapper::toResponse).toList();
    }

    private void recordDecision(PayrollPeriod period, PeriodDecisionAction action, PeriodStatus newStatus,
            String reason) {
        PayrollPeriodDecision decision = new PayrollPeriodDecision();
        decision.setPayrollPeriodDecisionId(UUID.randomUUID());
        decision.setBusinessId(period.getBusinessId());
        decision.setPeriodId(period.getPayrollPeriodId());
        decision.setAction(action);
        decision.setOldStatus(period.getStatus());
        decision.setNewStatus(newStatus);
        decision.setActedBy(TenantContext.current().userId());
        decision.setActedPermission(Permission.PAYROLL_APPROVE.name());
        decision.setReason(reason);
        decisionRepository.save(decision);

        period.setStatus(newStatus);
        period.setVersion(period.getVersion() + 1);
    }

    private PayrollPeriod requirePeriod(UUID periodId) {
        return periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
    }

    private long gapDaysBefore(PayrollPeriod period) {
        return periodRepository.findFirstByEndDateLessThanOrderByEndDateDesc(period.getStartDate())
                .map(previous -> ChronoUnit.DAYS.between(previous.getEndDate(), period.getStartDate()) - 1)
                .orElse(0L);
    }

    private PayrollPeriodResponse toResponse(PayrollPeriod period) {
        var lines = payrollLineRepository.findByPeriodId(period.getPayrollPeriodId());
        long lineCount = lines.size();
        long invalidCellCount = lines.stream().filter(l -> l.isHasInvalidCode()).count();
        long openIssueCount = issueRepository.countByPeriodIdAndAcknowledgedFalse(period.getPayrollPeriodId());
        boolean stale = payrollRunRepository.findFirstByPeriodIdOrderByStartedAtDesc(period.getPayrollPeriodId())
                .map(run -> isStale(period, run))
                .orElse(period.getTimesheetModifiedAt() != null);

        return PayrollPeriodResponse.builder()
                .payrollPeriodId(period.getPayrollPeriodId())
                .periodYear(period.getPeriodYear())
                .periodMonth(period.getPeriodMonth())
                .configId(period.getConfigId())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .daysInPeriod(period.getDaysInPeriod())
                .status(period.getStatus().name())
                .timesheetModifiedAt(period.getTimesheetModifiedAt())
                .lockedBy(period.getLockedBy())
                .lockedAt(period.getLockedAt())
                .version(period.getVersion())
                .lineCount(lineCount)
                .invalidCellCount(invalidCellCount)
                .openIssueCount(openIssueCount)
                .stale(stale)
                .gapDaysBefore(gapDaysBefore(period))
                .payslipCount(payslipRepository.countByPeriodId(period.getPayrollPeriodId()))
                .confirmedPayslipCount(confirmationRepository.countByPeriodIdAndStatus(
                        period.getPayrollPeriodId(), ConfirmationStatus.CONFIRMED))
                .build();
    }
}

package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.SendPayslipsRequest;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipEmailLogResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmployeeLeaveQuotaRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayslipEmailLogRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.service.PayslipAssembler;
import com.fnbx.hrm.service.PayslipService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.hrm.entity.PayslipConfirmation;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.repository.PayslipConfirmationRepository;
import com.fnbx.hrm.service.confirmation.PayslipConfirmationWorkflow;
import com.fnbx.hrm.service.confirmation.PayslipMailer;
import com.fnbx.hrm.service.payment.PaymentSynchronizer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayslipServiceImpl implements PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollLineItemRepository itemRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeLeaveQuotaRepository leaveQuotaRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final PayslipEmailLogRepository emailLogRepository;
    private final PayslipConfirmationRepository confirmationRepository;
    private final PayslipConfirmationWorkflow confirmationWorkflow;
    private final PayslipMailer mailer;
    private final PaymentSynchronizer paymentSynchronizer;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final PayslipAssembler assembler;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public List<PayslipResponse> generate(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        PayrollPeriod period = requirePeriod(periodId);
        requireNotStale(period);

        List<PayrollLine> lines = lineRepository.findByPeriodId(periodId);
        List<UUID> staffIds = lines.stream()
                .map(line -> assignmentRepository.findById(line.getAssignmentId()).map(EmploymentAssignment::getStaffId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        List<PayslipResponse> payslips = staffIds.stream().map(staffId -> generateOne(period, staffId)).toList();
        if (period.getStatus() != PeriodStatus.DRAFT) {
            paymentSynchronizer.sync(period);
        }
        return payslips;
    }

    private PayslipResponse generateOne(PayrollPeriod period, UUID staffId) {
        List<PayrollLine> employeeLines = lineRepository.findByPeriodId(period.getPayrollPeriodId()).stream()
                .filter(line -> assignmentRepository.findById(line.getAssignmentId())
                        .map(a -> a.getStaffId().equals(staffId)).orElse(false))
                .toList();

        Payslip payslip = payslipRepository.findByPeriodIdAndStaffId(period.getPayrollPeriodId(), staffId)
                .orElseGet(() -> {
                    Payslip created = new Payslip();
                    created.setPayslipId(UUID.randomUUID());
                    created.setBusinessId(period.getBusinessId());
                    created.setPeriodId(period.getPayrollPeriodId());
                    created.setStaffId(staffId);
                    return created;
                });

        BigDecimal gross = sum(employeeLines, PayrollLine::getGrossPay);
        BigDecimal employeeInsurance = sum(employeeLines, PayrollLine::getEmployeeInsuranceTotal);
        BigDecimal advanceTotal = employeeLines.stream()
                .flatMap(line -> itemRepository.findManualItems(line.getPayrollLineId()).stream())
                .filter(item -> "ADVANCE".equals(componentCodeOf(item)))
                .map(com.fnbx.hrm.entity.PayrollLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductionTotal = sum(employeeLines, PayrollLine::getDeductionTotal);
        BigDecimal netTotal = gross.subtract(deductionTotal);
        short lateDays = (short) employeeLines.stream().mapToInt(PayrollLine::getLateDayCount).sum();
        short lateShifts = (short) employeeLines.stream().mapToInt(PayrollLine::getLateShiftCount).sum();

        short year = (short) period.getEndDate().getYear();
        long unpaidLeaveDays = timesheetEntryRepository.countDistinctDatesByEmployeeAndCode(
                staffId, "KL", period.getStartDate(), period.getEndDate());
        long paidLeaveDays = timesheetEntryRepository.countDistinctDatesByEmployeeAndCode(
                staffId, "CP", period.getStartDate(), period.getEndDate());
        BigDecimal remaining = leaveQuotaRepository.findByStaffIdAndLeaveYear(staffId, year)
                .map(q -> q.getQuotaDays().subtract(q.getOpeningUsedDays()))
                .orElse(null);

        payslip.setPayrollLineCount((short) employeeLines.size());
        payslip.setGrossTotal(gross);
        payslip.setEmployeeInsuranceTotal(employeeInsurance);
        payslip.setAdvanceTotal(advanceTotal);
        payslip.setDeductionTotal(deductionTotal);
        payslip.setNetTotal(netTotal);
        payslip.setLateDayTotal(lateDays);
        payslip.setLateShiftTotal(lateShifts);
        payslip.setPaidLeaveDaysUsed(BigDecimal.valueOf(paidLeaveDays));
        payslip.setUnpaidLeaveDays(BigDecimal.valueOf(unpaidLeaveDays));
        payslip.setAnnualLeaveRemaining(remaining);
        payslip.setGeneratedAt(Instant.now());
        payslipRepository.save(payslip);

        employeeLines.forEach(line -> line.setPayslipId(payslip.getPayslipId()));
        lineRepository.saveAll(employeeLines);

        return assembler.toResponse(payslip);
    }

    private String componentCodeOf(com.fnbx.hrm.entity.PayrollLineItem item) {
        return entityManager.find(com.fnbx.hrm.entity.PayComponent.class, item.getComponentId()) instanceof
                com.fnbx.hrm.entity.PayComponent c ? c.getComponentCode() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayslipResponse> list(UUID periodId) {
        access.requireAllBusiness(Permission.PAYSLIP_ISSUE, Permission.HR_RECORD_READ);
        List<Payslip> payslips = payslipRepository.findByPeriodId(periodId);
        Map<UUID, PayslipConfirmation> confirmations = confirmationRepository
                .findByPayslipIdIn(payslips.stream().map(Payslip::getPayslipId).toList()).stream()
                .collect(Collectors.toMap(PayslipConfirmation::getPayslipId, Function.identity()));
        return payslips.stream().map(payslip -> assembler.toResponse(payslip, confirmations.get(payslip.getPayslipId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipDetailResponse get(UUID payslipId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(PayrollExceptions::payslipNotFound);
        List<com.fnbx.hrm.dto.response.PayrollLineResponse> lines = lineRepository
                .findLineResponses(payslip.getPeriodId(), null, null).stream()
                .filter(l -> l.getStaffId().equals(payslip.getStaffId()))
                .toList();
        return PayslipDetailResponse.builder()
                .payslip(assembler.toResponse(payslip))
                .lines(lines)
                .components(itemRepository.sumComponentsForEmployee(payslip.getPeriodId(), payslip.getStaffId()))
                .build();
    }

    @Override
    @Transactional
    public void send(UUID periodId, SendPayslipsRequest request) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        requirePaid(requirePeriod(periodId));
        boolean everyUnsent = request.getPayslipIds() == null || request.getPayslipIds().isEmpty();
        List<Payslip> targets = everyUnsent
                ? payslipRepository.findUnsentByPeriodId(periodId)
                : payslipRepository.findAllById(request.getPayslipIds());
        if (targets.stream().anyMatch(p -> !p.getPeriodId().equals(periodId))
                || (!everyUnsent && targets.size() != request.getPayslipIds().size())) {
            throw PayrollExceptions.payslipNotFound();
        }
        targets.forEach(this::deliver);
    }

    @Override
    @Transactional
    public void sendOne(UUID payslipId) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(PayrollExceptions::payslipNotFound);
        requirePaid(requirePeriod(payslip.getPeriodId()));
        deliver(payslip);
    }

    private void deliver(Payslip payslip) {
        mailer.deliver(payslip, confirmationWorkflow.issue(payslip));
    }

    private void requirePaid(PayrollPeriod period) {
        if (period.getStatus() != PeriodStatus.PAID) {
            throw PayrollExceptions.payslipPeriodNotPaid();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayslipEmailLogResponse> listEmailLogs(UUID payslipId) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        return emailLogRepository.findByPayslipIdOrderBySentAtDesc(payslipId).stream()
                .map(log -> PayslipEmailLogResponse.builder()
                        .payslipEmailLogId(log.getPayslipEmailLogId())
                        .recipientEmail(log.getRecipientEmail())
                        .subject(log.getSubject())
                        .status(log.getStatus().name())
                        .sentAt(log.getSentAt())
                        .errorMessage(log.getErrorMessage())
                        .build())
                .toList();
    }

    private void requireNotStale(PayrollPeriod period) {
        List<PayrollLine> lines = lineRepository.findByPeriodId(period.getPayrollPeriodId());
        boolean stale = lines.stream().anyMatch(l -> l.getCalculatedAt() == null
                || (period.getTimesheetModifiedAt() != null && period.getTimesheetModifiedAt().isAfter(l.getCalculatedAt())));
        if (stale) {
            throw PayrollExceptions.staleCalculation();
        }
    }

    private PayrollPeriod requirePeriod(UUID periodId) {
        return periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
    }

    private BigDecimal sum(List<PayrollLine> lines, java.util.function.Function<PayrollLine, BigDecimal> field) {
        return lines.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}

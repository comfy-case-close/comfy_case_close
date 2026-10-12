package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.CreatePayrollLineRequest;
import com.fnbx.hrm.dto.request.UpdatePayrollLineNoteRequest;
import com.fnbx.hrm.dto.response.PayrollLineSummaryResponse;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineInsuranceRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.SharedCostAllocationRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.service.PayrollLineBranchPolicy;
import com.fnbx.hrm.service.PayrollLineService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollLineServiceImpl implements PayrollLineService {

    private final PayrollPeriodRepository periodRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollLineBranchPolicy branchPolicy;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;
    private final PayrollLineItemRepository itemRepository;
    private final PayrollLineInsuranceRepository insuranceRepository;
    private final SharedCostAllocationRepository allocationRepository;
    private final TimesheetEntryRepository entryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PayrollLineSummaryResponse> listPayrollLines(UUID payrollPeriodId, EmploymentType employmentType,
            UUID branchId) {
        requirePeriod(payrollPeriodId);
        access.requireBusinessOrBranch(branchId, Permission.PAYROLL_PROCESS, Permission.TIMESHEET_READ);
        return lineRepository.findLineSummaries(payrollPeriodId, employmentType, branchId);
    }

    @Override
    @Transactional
    public PayrollLineSummaryResponse createPayrollLine(UUID payrollPeriodId, CreatePayrollLineRequest request) {
        PayrollPeriod period = requireDraftPeriod(payrollPeriodId);
        access.requireBusinessOrBranch(request.getBranchId(), Permission.PAYROLL_PROCESS, Permission.TIMESHEET_EDIT);

        EmploymentAssignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(PayrollExceptions::assignmentNotFound);
        if (!branchPolicy.isAllowed(assignment.getDefaultBranchId(), request.getBranchId())) {
            throw PayrollExceptions.branchNotAllowed(
                    "This assignment's default branch is not shared-pool, so it can only be placed there");
        }
        if (lineRepository.existsByPeriodIdAndAssignmentIdAndBranchId(
                payrollPeriodId, request.getAssignmentId(), request.getBranchId())) {
            throw PayrollExceptions.duplicatePayrollLine();
        }

        PayrollLine line = new PayrollLine();
        line.setPayrollLineId(UUID.randomUUID());
        line.setBusinessId(TenantContext.current().businessId());
        line.setPeriodId(payrollPeriodId);
        line.setAssignmentId(assignment.getEmploymentAssignmentId());
        line.setBranchId(request.getBranchId());
        line.setEmploymentType(assignment.getEmploymentType());
        try {
            lineRepository.saveAndFlush(line);
        } catch (DataIntegrityViolationException ex) {
            throw PayrollExceptions.duplicatePayrollLine();
        }
        markTimesheetModified(period);
        return requireSummary(payrollPeriodId, line.getPayrollLineId());
    }

    @Override
    @Transactional
    public int copyPayrollLines(UUID payrollPeriodId, UUID sourcePayrollPeriodId) {
        branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        PayrollPeriod period = requireDraftPeriod(payrollPeriodId);
        int copied = 0;
        for (PayrollLine source : lineRepository.findByPeriodId(sourcePayrollPeriodId)) {
            if (isExpired(source.getAssignmentId(), period)
                    || lineRepository.existsByPeriodIdAndAssignmentIdAndBranchId(
                            payrollPeriodId, source.getAssignmentId(), source.getBranchId())) {
                continue;
            }
            PayrollLine copy = new PayrollLine();
            copy.setPayrollLineId(UUID.randomUUID());
            copy.setBusinessId(source.getBusinessId());
            copy.setPeriodId(payrollPeriodId);
            copy.setAssignmentId(source.getAssignmentId());
            copy.setBranchId(source.getBranchId());
            copy.setEmploymentType(source.getEmploymentType());
            lineRepository.save(copy);
            copied++;
        }
        markTimesheetModified(period);
        return copied;
    }

    @Override
    @Transactional
    public PayrollLineSummaryResponse updatePayrollLineNote(UUID payrollLineId, UpdatePayrollLineNoteRequest request) {
        PayrollLine line = lineRepository.findById(payrollLineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
        access.requireBusinessOrBranch(line.getBranchId(), Permission.PAYROLL_PROCESS, Permission.TIMESHEET_EDIT);
        line.setNote(request.getNote());
        lineRepository.save(line);
        return requireSummary(line.getPeriodId(), payrollLineId);
    }

    @Override
    @Transactional
    public void deletePayrollLine(UUID payrollLineId, boolean force) {
        PayrollLine line = lineRepository.findById(payrollLineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
        PayrollPeriod period = requireDraftPeriod(line.getPeriodId());
        if (force) {
            branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        } else {
            access.requireBusinessOrBranch(line.getBranchId(), Permission.PAYROLL_PROCESS, Permission.TIMESHEET_EDIT);
            if (lineRepository.hasTimesheetEntries(payrollLineId) || itemRepository.hasManualItems(payrollLineId)) {
                throw PayrollExceptions.payrollLineHasTimesheet();
            }
        }
        List<UUID> lineIds = List.of(payrollLineId);
        insuranceRepository.deleteByLineIds(lineIds);
        allocationRepository.deleteByLineIds(lineIds);
        itemRepository.deleteAllByLine(payrollLineId);
        entryRepository.deleteAllByLine(payrollLineId);
        lineRepository.delete(line);
        markTimesheetModified(period);
    }

    private boolean isExpired(UUID assignmentId, PayrollPeriod period) {
        return assignmentRepository.findById(assignmentId)
                .map(a -> a.getEffectiveTo() != null && a.getEffectiveTo().isBefore(period.getStartDate()))
                .orElse(true);
    }

    private PayrollLineSummaryResponse requireSummary(UUID payrollPeriodId, UUID payrollLineId) {
        return lineRepository.findLineSummaries(payrollPeriodId, null, null).stream()
                .filter(summary -> summary.getPayrollLineId().equals(payrollLineId))
                .findFirst()
                .orElseThrow(PayrollExceptions::payrollLineNotFound);
    }

    private PayrollPeriod requirePeriod(UUID payrollPeriodId) {
        return periodRepository.findById(payrollPeriodId).orElseThrow(PayrollExceptions::periodNotFound);
    }

    private PayrollPeriod requireDraftPeriod(UUID payrollPeriodId) {
        PayrollPeriod period = requirePeriod(payrollPeriodId);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("Payroll lines can only be edited while the period is a draft");
        }
        return period;
    }

    private void markTimesheetModified(PayrollPeriod period) {
        period.setTimesheetModifiedAt(Instant.now());
        periodRepository.save(period);
    }
}

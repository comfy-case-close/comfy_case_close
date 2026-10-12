package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.BranchRevenueEntryRequest;
import com.fnbx.hrm.dto.response.BranchLaborCostResponse;
import com.fnbx.hrm.repository.BranchCostTotals;
import com.fnbx.hrm.dto.response.PayrollDashboardResponse;
import com.fnbx.hrm.dto.response.EmployeePeriodSummaryResponse;
import com.fnbx.hrm.dto.response.ReconciliationCheckResponse;
import com.fnbx.hrm.dto.response.SharedCostAllocationResponse;
import com.fnbx.hrm.entity.BranchRevenue;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.SharedCostAllocation;
import com.fnbx.hrm.repository.BranchRevenueRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.SharedCostAllocationRepository;
import com.fnbx.hrm.service.PayrollReportService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.hrm.repository.BranchComponentTotals;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.service.report.AllowanceSplit;
import com.fnbx.hrm.service.report.PayBucket;
import com.fnbx.hrm.service.report.ReportPeriodSelector;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollReportServiceImpl implements PayrollReportService {

    private final PayrollLineRepository lineRepository;
    private final BranchRevenueRepository revenueRepository;
    private final SharedCostAllocationRepository allocationRepository;
    private final PayrollLineItemRepository itemRepository;
    private final ReportPeriodSelector periodSelector;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public PayrollDashboardResponse getDashboard(UUID periodId, YearMonth from, YearMonth to) {
        branchAccess.requireBusiness(Permission.PAYROLL_REPORT_READ);
        List<UUID> periodIds = periodSelector.select(periodId, from, to);
        List<PayrollLine> lines = lineRepository.findByPeriodIdIn(periodIds);
        BigDecimal totalLaborCost = sum(lines, PayrollLine::getLaborCost);
        BigDecimal totalGross = sum(lines, PayrollLine::getGrossPay);
        BigDecimal employerInsurance = sum(lines, PayrollLine::getEmployerInsuranceTotal);
        long paidHeadcount = lineRepository.countPaidStaff(periodIds);
        BigDecimal costPerHead = paidHeadcount == 0 ? BigDecimal.ZERO
                : totalLaborCost.divide(BigDecimal.valueOf(paidHeadcount), 0, RoundingMode.HALF_UP);
        BigDecimal revenue = periodIds.stream()
                .map(id -> revenueRepository.findByPeriodIdAndBranchIdIsNull(id)
                        .map(BranchRevenue::getRevenueAmount).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal laborCostRatio = revenue.signum() == 0 ? null
                : totalLaborCost.divide(revenue, 6, RoundingMode.HALF_UP);
        AllowanceSplit split = splitOf(itemRepository.sumComponentsByBranch(periodIds));

        return PayrollDashboardResponse.builder()
                .totalLaborCost(totalLaborCost)
                .totalGross(totalGross)
                .employerInsurance(employerInsurance)
                .paidHeadcount(paidHeadcount)
                .costPerHead(costPerHead)
                .revenue(revenue)
                .laborCostRatio(laborCostRatio)
                .payBeforeAllowance(split.payBeforeAllowance())
                .allowancePay(split.allowancePay())
                .payAfterAllowance(split.payAfterAllowance())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchLaborCostResponse> getBranchLaborCost(UUID periodId, YearMonth from, YearMonth to) {
        branchAccess.requireBusiness(Permission.PAYROLL_REPORT_READ);
        List<UUID> periodIds = periodSelector.select(periodId, from, to);
        Map<List<Object>, BranchLaborCostResponse.BranchLaborCostResponseBuilder> rows = new LinkedHashMap<>();
        for (BranchCostTotals direct : lineRepository.findDirectBranchCost(periodIds)) {
            rows.put(List.of(direct.branchId(), direct.employmentType()), BranchLaborCostResponse.builder()
                    .branchId(direct.branchId()).employmentType(direct.employmentType()).lineCount(direct.lineCount())
                    .gross(direct.gross()).employerInsurance(direct.employerInsurance()).laborCost(direct.laborCost())
                    .allocatedGross(BigDecimal.ZERO).allocatedLaborCost(BigDecimal.ZERO));
        }
        for (BranchCostTotals allocated : lineRepository.findAllocatedBranchCost(periodIds)) {
            BranchLaborCostResponse.BranchLaborCostResponseBuilder row = rows.computeIfAbsent(
                    List.of(allocated.branchId(), allocated.employmentType()), key -> BranchLaborCostResponse.builder()
                            .branchId(allocated.branchId()).employmentType(allocated.employmentType()).lineCount(0)
                            .gross(BigDecimal.ZERO).employerInsurance(BigDecimal.ZERO).laborCost(BigDecimal.ZERO));
            BranchLaborCostResponse current = row.build();
            row.gross(current.getGross().add(allocated.gross()))
                    .employerInsurance(current.getEmployerInsurance().add(allocated.employerInsurance()))
                    .laborCost(current.getLaborCost().add(allocated.laborCost()))
                    .allocatedGross(allocated.gross()).allocatedLaborCost(allocated.laborCost());
        }
        Map<List<Object>, AllowanceSplit> splits = splitsByBranch(itemRepository.sumComponentsByBranch(periodIds));
        return rows.entrySet().stream()
                .map(row -> withSplit(row.getValue().build(), splits.getOrDefault(row.getKey(), AllowanceSplit.ZERO)))
                .toList();
    }

    private BranchLaborCostResponse withSplit(BranchLaborCostResponse row, AllowanceSplit split) {
        return row.toBuilder()
                .payBeforeAllowance(split.payBeforeAllowance())
                .allowancePay(split.allowancePay())
                .payAfterAllowance(split.payAfterAllowance())
                .build();
    }

    private AllowanceSplit splitOf(List<BranchComponentTotals> totals) {
        AllowanceSplit split = AllowanceSplit.ZERO;
        for (BranchComponentTotals total : totals) {
            split = split.plus(PayBucket.of(total.componentCode()), total.amount());
        }
        return split;
    }

    private Map<List<Object>, AllowanceSplit> splitsByBranch(List<BranchComponentTotals> totals) {
        Map<List<Object>, AllowanceSplit> splits = new LinkedHashMap<>();
        for (BranchComponentTotals total : totals) {
            splits.merge(List.of(total.branchId(), total.employmentType()),
                    AllowanceSplit.ZERO.plus(PayBucket.of(total.componentCode()), total.amount()),
                    (left, right) -> new AllowanceSplit(left.payBeforeAllowance().add(right.payBeforeAllowance()),
                            left.allowancePay().add(right.allowancePay())));
        }
        return splits;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeePeriodSummaryResponse> getEmployeeSummary(UUID periodId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return lineRepository.findEmployeeSummaries(periodId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SharedCostAllocationResponse> getSharedCostAllocation(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYROLL_REPORT_READ);
        return allocationRepository.findByPeriodId(periodId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationCheckResponse> getReconciliation(UUID periodId) {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_APPROVE);
        List<PayrollLine> lines = lineRepository.findByPeriodId(periodId);
        BigDecimal laborCostTotal = sum(lines, PayrollLine::getLaborCost);
        BigDecimal netPlusDeductionsPlusEmployerInsurance = lines.stream()
                .map(l -> l.getNetPay().add(l.getDeductionTotal()).add(l.getEmployerInsuranceTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDelta = netPlusDeductionsPlusEmployerInsurance.subtract(laborCostTotal);

        List<SharedCostAllocation> allocations = allocationRepository.findByPeriodId(periodId);
        BigDecimal allocatedGross = sum(allocations, SharedCostAllocation::getAllocatedGross);
        BigDecimal sharedPoolGross = lines.stream()
                .filter(l -> allocations.stream().anyMatch(a -> a.getPayrollLineId().equals(l.getPayrollLineId())))
                .map(PayrollLine::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocationDelta = allocatedGross.subtract(sharedPoolGross);

        return List.of(
                ReconciliationCheckResponse.builder()
                        .checkCode("RECON_TOTAL_MISMATCH")
                        .description("Net + deductions + employer insurance vs labor cost")
                        .delta(totalDelta)
                        .ok(totalDelta.abs().compareTo(BigDecimal.valueOf(lines.size())) <= 0)
                        .build(),
                ReconciliationCheckResponse.builder()
                        .checkCode("SHARED_COST_ALLOCATION")
                        .description("Allocated gross vs shared-pool line gross")
                        .delta(allocationDelta)
                        .ok(allocationDelta.abs().compareTo(BigDecimal.valueOf(Math.max(allocations.size(), 1))) <= 0)
                        .build());
    }

    @Override
    @Transactional
    public void setBranchRevenues(UUID periodId, List<BranchRevenueEntryRequest> entries) {
        branchAccess.requireBusiness(Permission.PAYROLL_PROCESS);
        for (BranchRevenueEntryRequest entry : entries) {
            BranchRevenue revenue = (entry.getBranchId() == null
                    ? revenueRepository.findByPeriodIdAndBranchIdIsNull(periodId)
                    : revenueRepository.findByPeriodIdAndBranchId(periodId, entry.getBranchId()))
                    .orElseGet(() -> {
                        BranchRevenue created = new BranchRevenue();
                        created.setBranchRevenueId(UUID.randomUUID());
                        created.setBusinessId(TenantContext.current().businessId());
                        created.setPeriodId(periodId);
                        created.setBranchId(entry.getBranchId());
                        return created;
                    });
            revenue.setRevenueAmount(entry.getAmount());
            revenue.setRecordedAt(Instant.now());
            revenueRepository.save(revenue);
        }
    }

    private SharedCostAllocationResponse toResponse(SharedCostAllocation allocation) {
        return SharedCostAllocationResponse.builder()
                .payrollLineId(allocation.getPayrollLineId())
                .targetBranchId(allocation.getTargetBranchId())
                .allocationRatio(allocation.getAllocationRatio())
                .allocatedCost(allocation.getAllocatedCost())
                .allocatedGross(allocation.getAllocatedGross())
                .allocatedEmployerInsurance(allocation.getAllocatedEmployerInsurance())
                .allocatedBasePay(allocation.getAllocatedBasePay())
                .build();
    }

    private <T> BigDecimal sum(List<T> values, java.util.function.Function<T, BigDecimal> field) {
        return values.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

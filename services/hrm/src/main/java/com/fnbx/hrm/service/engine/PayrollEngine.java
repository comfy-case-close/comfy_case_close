package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.entity.EmployeeAllowance;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.InsuranceScheme;
import com.fnbx.hrm.entity.PayComponent;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollLineInsurance;
import com.fnbx.hrm.entity.PayrollLineItem;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.SharedCostAllocation;
import com.fnbx.hrm.entity.TimesheetEntry;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.enums.IssueCode;
import com.fnbx.hrm.enums.IssueSeverity;
import com.fnbx.hrm.enums.ProrationBasis;
import com.fnbx.hrm.repository.AttendanceCodeRepository;
import com.fnbx.hrm.repository.BranchSettingRepository;
import com.fnbx.hrm.repository.EmployeeAllowanceRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.InsuranceSchemeRepository;
import com.fnbx.hrm.repository.PayComponentRepository;
import com.fnbx.hrm.repository.PayrollLineInsuranceRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.SharedCostAllocationRepository;
import com.fnbx.hrm.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * One idempotent run over a period's roster and timesheet (spec section 6.2).
 * Every step re-derives its output from scratch - re-running twice on
 * unchanged input produces the same numbers.
 */
@Component
@RequiredArgsConstructor
public class PayrollEngine {

    private final PayrollLineRepository lineRepository;
    private final TimesheetEntryRepository entryRepository;
    private final PayrollLineItemRepository itemRepository;
    private final PayrollLineInsuranceRepository insuranceRepository;
    private final SharedCostAllocationRepository allocationRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final EmployeeAllowanceRepository allowanceRepository;
    private final PayComponentRepository componentRepository;
    private final InsuranceSchemeRepository schemeRepository;
    private final AttendanceCodeRepository attendanceCodeRepository;
    private final BranchSettingRepository branchSettingRepository;
    private final HourAggregator hourAggregator;
    private final BirthdayAllowanceCalculator birthdayAllowance;
    private final List<PeriodIssueContributor> issueContributors;

    public record EngineResult(int linesCalculated, List<DataValidationIssue> issues) {
        public boolean hasError() {
            return issues.stream().anyMatch(i -> i.getSeverity() == IssueSeverity.ERROR);
        }
    }

    public EngineResult run(PayrollPeriod period, PayrollConfig config) {
        List<PayrollLine> lines = lineRepository.findByPeriodId(period.getPayrollPeriodId());
        lines.sort(Comparator.comparing(PayrollLine::getPayrollLineId));
        List<UUID> lineIds = lines.stream().map(PayrollLine::getPayrollLineId).toList();

        Map<UUID, EmploymentAssignment> assignmentsById = lines.stream()
                .map(PayrollLine::getAssignmentId).distinct()
                .map(id -> assignmentRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(EmploymentAssignment::getEmploymentAssignmentId, Function.identity()));

        Map<UUID, List<TimesheetEntry>> entriesByLine = entryRepository.findByPayrollLineIdIn(lineIds).stream()
                .collect(Collectors.groupingBy(TimesheetEntry::getPayrollLineId));
        Set<UUID> absenceCodeIds = attendanceCodeRepository.findAll().stream()
                .filter(AttendanceCode::isCountsAsAbsence)
                .map(AttendanceCode::getAttendanceCodeId)
                .collect(Collectors.toSet());

        for (PayrollLine line : lines) {
            aggregateHours(line, entriesByLine.getOrDefault(line.getPayrollLineId(), List.of()), config, absenceCodeIds);
            snapshotContract(line, assignmentsById.get(line.getAssignmentId()));
        }
        lineRepository.saveAll(lines);

        itemRepository.deleteEngineComputedByLineIds(lineIds);
        insuranceRepository.deleteByLineIds(lineIds);
        allocationRepository.deleteByLineIds(lineIds);

        Map<String, PayComponent> componentsByCode = componentRepository.findAll().stream()
                .collect(Collectors.toMap(PayComponent::getComponentCode, Function.identity()));

        List<PayrollLineItem> engineItems = new ArrayList<>();
        for (PayrollLine line : lines) {
            engineItems.addAll(lineLocalComponents(line, config, componentsByCode));
        }
        engineItems.addAll(prorateResponsibilityAllowance(lines, assignmentsById, config, componentsByCode));
        engineItems.addAll(prorateEmployeeAllowances(lines, assignmentsById, config, componentsByCode));
        engineItems.addAll(birthdayAllowance.items(period, config, lines, assignmentsById,
                componentsByCode.get(PayComponentCode.BIRTHDAY_ALLOWANCE)));
        itemRepository.saveAll(engineItems);

        List<PayrollLineInsurance> insuranceRows = computeInsurance(lines, assignmentsById);
        insuranceRepository.saveAll(insuranceRows);

        applyLineTotals(lines, componentsByCode);
        lineRepository.saveAll(lines);

        allocationRepository.saveAll(computeSharedCostAllocation(lines));

        List<DataValidationIssue> issues = validate(period, lines, entriesByLine);
        issueContributors.forEach(contributor -> issues.addAll(contributor.issuesFor(period, config, lines)));

        Instant now = Instant.now();
        lines.forEach(l -> l.setCalculatedAt(now));
        lineRepository.saveAll(lines);

        return new EngineResult(lines.size(), issues);
    }

    // ---- step 2: hour aggregation ------------------------------------------

    private void aggregateHours(PayrollLine line, List<TimesheetEntry> entries, PayrollConfig config,
            Set<UUID> absenceCodeIds) {
        HourAggregator.LineHours hours = hourAggregator.aggregate(entries, config.getStandardHoursPerDay(), absenceCodeIds);
        line.setTotalHours(hours.totalHours());
        line.setStandardHours(hours.standardHours());
        line.setOvertimeHours(hours.overtimeHours());
        line.setWeekendHours(hours.weekendHours());
        line.setRegularHours(hours.regularHours());
        line.setStandardWorkdays(hours.standardWorkdays());
        line.setOvertimeDays(hours.overtimeDays());
        line.setWeekendDays(hours.weekendDays());
        line.setAllowanceHours(hours.allowanceHours());
        line.setAllowanceWorkdays(hours.allowanceWorkdays());
        line.setLateDayCount((short) hours.lateDayCount());
        line.setLateShiftCount((short) hours.lateShiftCount());
        line.setAbsenceDayCount((short) hours.absenceDayCount());
        line.setHasInvalidCode(hours.hasInvalidCode());
    }

    // ---- step 3: snapshot ---------------------------------------------------

    private void snapshotContract(PayrollLine line, EmploymentAssignment assignment) {
        if (assignment == null) {
            return;
        }
        line.setEmploymentType(assignment.getEmploymentType());
        line.setSnapBaseSalary(assignment.getMonthlyBaseSalary());
        line.setSnapHourlyRate(assignment.getHourlyBaseRate());
        line.setSnapSupplementAllowance(assignment.getSupplementAllowance());
        line.setSnapKpiAllowance(assignment.getKpiAllowance());
        line.setSnapResponsibilityAllowance(assignment.getResponsibilityAllowance());
    }

    // ---- step 5: line-local components ---------------------------------------

    private List<PayrollLineItem> lineLocalComponents(PayrollLine line, PayrollConfig config,
            Map<String, PayComponent> components) {
        boolean fulltime = line.getEmploymentType() == EmploymentType.FULLTIME;
        BigDecimal standardDaysPerMonth = config.getStandardDaysPerMonth();
        List<PayrollLineItem> items = new ArrayList<>();

        if (fulltime) {
            addItem(items, components, PayComponentCode.BASE_PAY, line,
                    line.getStandardWorkdays(), rate(line.getSnapBaseSalary(), standardDaysPerMonth),
                    "%s days x %s".formatted(line.getStandardWorkdays(), rate(line.getSnapBaseSalary(), standardDaysPerMonth)));
            addItem(items, components, PayComponentCode.SALARY_SUPPLEMENT, line,
                    line.getStandardWorkdays(), rate(line.getSnapSupplementAllowance(), standardDaysPerMonth), null);
            addItem(items, components, PayComponentCode.KPI_ALW, line,
                    line.getStandardWorkdays(), rate(line.getSnapKpiAllowance(), standardDaysPerMonth), null);
            addItem(items, components, PayComponentCode.OT_PAY, line,
                    line.getOvertimeDays(), config.getOvertimeMultiplier().multiply(rate(line.getSnapBaseSalary(), standardDaysPerMonth)), null);
            BigDecimal weekendRate = config.getWeekendMultiplier().subtract(BigDecimal.ONE)
                    .multiply(rate(line.getSnapBaseSalary(), standardDaysPerMonth))
                    .divide(nonZero(config.getStandardHoursPerDay()), 6, RoundingMode.HALF_UP);
            addItem(items, components, PayComponentCode.WEEKEND_PREMIUM, line, line.getWeekendHours(), weekendRate, null);
        } else {
            addItem(items, components, PayComponentCode.BASE_PAY, line,
                    line.getRegularHours(), line.getSnapHourlyRate(), null);
            addItem(items, components, PayComponentCode.OT_PAY, line,
                    line.getOvertimeHours(), config.getOvertimeMultiplier().multiply(orZero(line.getSnapHourlyRate())), null);
            BigDecimal weekendRate = config.getWeekendMultiplier().subtract(BigDecimal.ONE)
                    .multiply(orZero(line.getSnapHourlyRate()));
            addItem(items, components, PayComponentCode.WEEKEND_PREMIUM, line, line.getWeekendHours(), weekendRate, null);
        }
        return items;
    }

    private void addItem(List<PayrollLineItem> items, Map<String, PayComponent> components, String code,
            PayrollLine line, BigDecimal quantity, BigDecimal rate, String note) {
        PayComponent component = components.get(code);
        if (component == null || quantity == null || rate == null) {
            return;
        }
        BigDecimal amount = quantity.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        // Engine-computed items with amount = 0 are not stored (spec section 5.5).
        if (amount.signum() == 0) {
            return;
        }
        PayrollLineItem item = new PayrollLineItem();
        item.setPayrollLineItemId(UUID.randomUUID());
        item.setBusinessId(line.getBusinessId());
        item.setPayrollLineId(line.getPayrollLineId());
        item.setComponentId(component.getPayComponentId());
        item.setQuantity(quantity.setScale(2, RoundingMode.HALF_UP));
        item.setRate(rate.setScale(2, RoundingMode.HALF_UP));
        item.setAmount(amount);
        item.setCalcNote(note != null ? note : quantity + " x " + rate);
        items.add(item);
    }

    // ---- step 6a: RESP_ALW, scope ASSIGNMENT ---------------------------------

    private List<PayrollLineItem> prorateResponsibilityAllowance(List<PayrollLine> lines,
            Map<UUID, EmploymentAssignment> assignments, PayrollConfig config, Map<String, PayComponent> components) {
        PayComponent component = components.get(PayComponentCode.RESP_ALW);
        if (component == null) {
            return List.of();
        }
        List<PayrollLineItem> items = new ArrayList<>();
        Map<UUID, List<PayrollLine>> byAssignment = lines.stream()
                .collect(Collectors.groupingBy(PayrollLine::getAssignmentId));
        for (var entry : byAssignment.entrySet()) {
            EmploymentAssignment assignment = assignments.get(entry.getKey());
            if (assignment == null || assignment.getResponsibilityAllowance() == null
                    || assignment.getResponsibilityAllowance().signum() == 0) {
                continue;
            }
            ProrationBasis basis = assignment.isFixedSalary() ? ProrationBasis.FULL_SPLIT : ProrationBasis.DAYS_CAPPED;
            items.addAll(prorate(entry.getValue(), assignment.getResponsibilityAllowance(), basis,
                    config.getStandardDaysPerMonth(), component));
        }
        return items;
    }

    // ---- step 6b: 4 person-level allowances, scope EMPLOYEE ------------------

    private List<PayrollLineItem> prorateEmployeeAllowances(List<PayrollLine> lines,
            Map<UUID, EmploymentAssignment> assignments, PayrollConfig config, Map<String, PayComponent> components) {
        List<PayrollLineItem> items = new ArrayList<>();
        Map<UUID, List<PayrollLine>> byStaff = lines.stream()
                .filter(l -> assignments.get(l.getAssignmentId()) != null)
                .collect(Collectors.groupingBy(l -> assignments.get(l.getAssignmentId()).getStaffId()));

        for (var entry : byStaff.entrySet()) {
            EmployeeAllowance allowance = allowanceRepository.findEffective(entry.getKey(), java.time.LocalDate.now())
                    .orElse(null);
            if (allowance == null) {
                continue;
            }
            List<PayrollLine> staffLines = entry.getValue();
            items.addAll(prorateIfPresent(staffLines, allowance.getLunchAllowance(), ProrationBasis.DAYS_CAPPED,
                    config.getStandardDaysPerMonth(), components.get(PayComponentCode.LUNCH_ALW)));
            items.addAll(prorateIfPresent(staffLines, allowance.getHousingAllowance(), ProrationBasis.FULL_SPLIT,
                    config.getStandardDaysPerMonth(), components.get(PayComponentCode.HOUSING_ALW)));
            items.addAll(prorateIfPresent(staffLines, allowance.getPhoneAllowance(), ProrationBasis.FULL_SPLIT,
                    config.getStandardDaysPerMonth(), components.get(PayComponentCode.PHONE_ALW)));
            items.addAll(prorateIfPresent(staffLines, allowance.getFuelAllowance(), ProrationBasis.FULL_SPLIT,
                    config.getStandardDaysPerMonth(), components.get(PayComponentCode.FUEL_ALW)));
        }
        return items;
    }

    private List<PayrollLineItem> prorateIfPresent(List<PayrollLine> group, BigDecimal amount, ProrationBasis basis,
            BigDecimal standardDaysPerMonth, PayComponent component) {
        if (component == null || amount == null || amount.signum() == 0) {
            return List.of();
        }
        return prorate(group, amount, basis, standardDaysPerMonth, component);
    }

    /**
     * {@code DAYS_CAPPED}: {@code amount(line) = A * MIN(sum(allowanceWorkdays)/D, 1) * share(line)} -
     * can never exceed 100% of {@code A} (R05). {@code FULL_SPLIT}:
     * {@code amount(line) = A * share(line)}, always summing to exactly {@code A}.
     * {@code share(line) = allowanceHours / sum(allowanceHours)}; when the group's
     * hours are all zero, the whole amount goes to the lowest-id line (R08's tie-break).
     */
    private List<PayrollLineItem> prorate(List<PayrollLine> group, BigDecimal amount, ProrationBasis basis,
            BigDecimal standardDaysPerMonth, PayComponent component) {
        List<PayrollLine> sorted = group.stream()
                .sorted(Comparator.comparing(PayrollLine::getPayrollLineId)).toList();
        List<BigDecimal> weights = weightsByAllowanceHours(sorted);

        BigDecimal factor = BigDecimal.ONE;
        if (basis == ProrationBasis.DAYS_CAPPED) {
            BigDecimal sumWorkdays = sorted.stream().map(PayrollLine::getAllowanceWorkdays)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            factor = sumWorkdays.divide(nonZero(standardDaysPerMonth), 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        BigDecimal totalToSplit = amount.multiply(factor).setScale(0, RoundingMode.HALF_UP);
        if (totalToSplit.signum() == 0) {
            return List.of();
        }
        List<BigDecimal> allocated = LargestRemainderAllocator.allocate(totalToSplit, weights);

        List<PayrollLineItem> items = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            BigDecimal lineAmount = allocated.get(i);
            if (lineAmount.signum() == 0) {
                continue;
            }
            PayrollLine line = sorted.get(i);
            PayrollLineItem item = new PayrollLineItem();
            item.setPayrollLineItemId(UUID.randomUUID());
            item.setBusinessId(line.getBusinessId());
            item.setPayrollLineId(line.getPayrollLineId());
            item.setComponentId(component.getPayComponentId());
            item.setAmount(lineAmount);
            item.setCalcNote("%s x share of %s".formatted(basis, amount));
            items.add(item);
        }
        return items;
    }

    private List<BigDecimal> weightsByAllowanceHours(List<PayrollLine> sortedLines) {
        List<BigDecimal> weights = sortedLines.stream().map(PayrollLine::getAllowanceHours).toList();
        boolean allZero = weights.stream().allMatch(w -> w.signum() == 0);
        if (!allZero) {
            return weights;
        }
        List<BigDecimal> tieBreak = new ArrayList<>();
        for (int i = 0; i < sortedLines.size(); i++) {
            tieBreak.add(i == 0 ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        return tieBreak;
    }

    // ---- step 7: insurance ---------------------------------------------------

    /** R08: the contract's base ((5)+(7)+(8), full value) split across its own lines by hours. */
    private List<PayrollLineInsurance> computeInsurance(List<PayrollLine> lines,
            Map<UUID, EmploymentAssignment> assignments) {
        List<InsuranceScheme> schemes = schemeRepository.findAllEffective(java.time.LocalDate.now());
        if (schemes.isEmpty()) {
            return List.of();
        }
        List<PayrollLineInsurance> rows = new ArrayList<>();
        Map<UUID, List<PayrollLine>> byAssignment = lines.stream()
                .collect(Collectors.groupingBy(PayrollLine::getAssignmentId));

        for (var entry : byAssignment.entrySet()) {
            EmploymentAssignment assignment = assignments.get(entry.getKey());
            if (assignment == null || !assignment.isInsured()) {
                continue;
            }
            BigDecimal base = orZero(assignment.getMonthlyBaseSalary())
                    .add(orZero(assignment.getKpiAllowance()))
                    .add(orZero(assignment.getResponsibilityAllowance()));
            if (base.signum() == 0) {
                continue;
            }
            List<PayrollLine> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(PayrollLine::getPayrollLineId)).toList();
            List<BigDecimal> weights = weightsByAllowanceHours(sorted);
            List<BigDecimal> shares = LargestRemainderAllocator.allocate(base.setScale(0, RoundingMode.HALF_UP), weights);

            for (int i = 0; i < sorted.size(); i++) {
                PayrollLine line = sorted.get(i);
                BigDecimal insuranceBase = shares.get(i);
                for (InsuranceScheme scheme : schemes) {
                    PayrollLineInsurance row = new PayrollLineInsurance();
                    row.setPayrollLineInsuranceId(UUID.randomUUID());
                    row.setBusinessId(line.getBusinessId());
                    row.setPayrollLineId(line.getPayrollLineId());
                    row.setSchemeId(scheme.getInsuranceSchemeId());
                    row.setInsuranceBase(insuranceBase);
                    row.setEmployerRate(scheme.getEmployerRate());
                    row.setEmployeeRate(scheme.getEmployeeRate());
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    // ---- step 8: line totals --------------------------------------------------

    private void applyLineTotals(List<PayrollLine> lines, Map<String, PayComponent> componentsByCode) {
        Map<UUID, PayComponent> componentsById = componentsByCode.values().stream()
                .collect(Collectors.toMap(PayComponent::getPayComponentId, Function.identity()));
        List<UUID> lineIds = lines.stream().map(PayrollLine::getPayrollLineId).toList();

        Map<UUID, List<PayrollLineItem>> itemsByLine = itemRepository.findByPayrollLineIdIn(lineIds).stream()
                .collect(Collectors.groupingBy(PayrollLineItem::getPayrollLineId));
        Map<UUID, List<PayrollLineInsurance>> insuranceByLine = insuranceRepository.findByPayrollLineIdIn(lineIds).stream()
                .collect(Collectors.groupingBy(PayrollLineInsurance::getPayrollLineId));

        for (PayrollLine line : lines) {
            List<PayrollLineItem> items = itemsByLine.getOrDefault(line.getPayrollLineId(), List.of());
            BigDecimal gross = sumByType(items, componentsById, com.fnbx.hrm.enums.ComponentType.EARNING);
            BigDecimal deductionsFromItems = sumByType(items, componentsById, com.fnbx.hrm.enums.ComponentType.DEDUCTION);

            List<PayrollLineInsurance> insurances = insuranceByLine.getOrDefault(line.getPayrollLineId(), List.of());
            BigDecimal employeeInsurance = insurances.stream().map(PayrollLineInsurance::getEmployeeAmount)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal employerInsurance = insurances.stream().map(PayrollLineInsurance::getEmployerAmount)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal insuranceBase = insurances.stream().map(PayrollLineInsurance::getInsuranceBase)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(Math.max(insurances.size(), 1)), 0, RoundingMode.HALF_UP);

            BigDecimal deductionTotal = deductionsFromItems.add(employeeInsurance);
            line.setGrossPay(gross);
            line.setInsuranceBase(insurances.isEmpty() ? BigDecimal.ZERO : insuranceBase);
            line.setEmployeeInsuranceTotal(employeeInsurance);
            line.setEmployerInsuranceTotal(employerInsurance);
            line.setDeductionTotal(deductionTotal);
            line.setNetPay(gross.subtract(deductionTotal));
            line.setLaborCost(gross.add(employerInsurance));
        }
    }

    private BigDecimal sumByType(List<PayrollLineItem> items, Map<UUID, PayComponent> componentsById,
            com.fnbx.hrm.enums.ComponentType type) {
        return items.stream()
                .filter(item -> {
                    PayComponent component = componentsById.get(item.getComponentId());
                    return component != null && component.getComponentType() == type;
                })
                .map(PayrollLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- step 9: shared-cost allocation ---------------------------------------

    private List<SharedCostAllocation> computeSharedCostAllocation(List<PayrollLine> lines) {
        List<UUID> sellingStores = branchSettingRepository.findActiveSellingStoreBranchIds();
        if (sellingStores.isEmpty()) {
            return List.of();
        }
        Set<UUID> sharedPoolBranches = Set.copyOf(branchSettingRepository.findActiveSharedPoolBranchIds());

        List<SharedCostAllocation> allocations = new ArrayList<>();
        int n = sellingStores.size();
        List<BigDecimal> equalWeights = sellingStores.stream().map(id -> BigDecimal.ONE).toList();
        BigDecimal ratio = BigDecimal.ONE.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);

        for (PayrollLine line : lines) {
            if (!sharedPoolBranches.contains(line.getBranchId())) {
                continue;
            }
            List<BigDecimal> costShares = LargestRemainderAllocator.allocate(
                    line.getLaborCost().setScale(0, RoundingMode.HALF_UP), equalWeights);
            List<BigDecimal> grossShares = LargestRemainderAllocator.allocate(
                    line.getGrossPay().setScale(0, RoundingMode.HALF_UP), equalWeights);
            List<BigDecimal> insuranceShares = LargestRemainderAllocator.allocate(
                    line.getEmployerInsuranceTotal().setScale(0, RoundingMode.HALF_UP), equalWeights);

            for (int i = 0; i < n; i++) {
                SharedCostAllocation allocation = new SharedCostAllocation();
                allocation.setSharedCostAllocationId(UUID.randomUUID());
                allocation.setBusinessId(line.getBusinessId());
                allocation.setPayrollLineId(line.getPayrollLineId());
                allocation.setTargetBranchId(sellingStores.get(i));
                allocation.setAllocationRatio(ratio);
                allocation.setAllocatedCost(costShares.get(i));
                allocation.setAllocatedGross(grossShares.get(i));
                allocation.setAllocatedEmployerInsurance(insuranceShares.get(i));
                allocation.setAllocatedBasePay(grossShares.get(i));
                allocations.add(allocation);
            }
        }
        return allocations;
    }

    // ---- step 11: validation ---------------------------------------------------

    private List<DataValidationIssue> validate(PayrollPeriod period, List<PayrollLine> lines,
            Map<UUID, List<TimesheetEntry>> entriesByLine) {
        List<DataValidationIssue> issues = new ArrayList<>();
        Instant now = Instant.now();

        for (PayrollLine line : lines) {
            if (line.isHasInvalidCode()) {
                issues.add(issue(period, IssueCode.INVALID_ATTENDANCE_CODE, IssueSeverity.ERROR,
                        "line:" + line.getPayrollLineId(), "Line has an invalid attendance code", now));
            }
            boolean missingRate = line.getEmploymentType() == EmploymentType.FULLTIME
                    ? line.getSnapBaseSalary() == null
                    : line.getSnapHourlyRate() == null;
            if (missingRate) {
                issues.add(issue(period, IssueCode.MISSING_CONTRACT_RATE, IssueSeverity.ERROR,
                        "line:" + line.getPayrollLineId(), "Contract rate is missing for this line's employment type", now));
            }
        }

        issues.addAll(findBranchConflicts(period, entriesByLine, lines));

        BigDecimal netPlusDeductionsPlusEmployerInsurance = lines.stream()
                .map(l -> l.getNetPay().add(l.getDeductionTotal()).add(l.getEmployerInsuranceTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal laborCostTotal = lines.stream().map(PayrollLine::getLaborCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netPlusDeductionsPlusEmployerInsurance.subtract(laborCostTotal).abs().compareTo(BigDecimal.valueOf(lines.size())) > 0) {
            issues.add(issue(period, IssueCode.RECON_TOTAL_MISMATCH, IssueSeverity.ERROR, null,
                    "Net + deductions + employer insurance does not equal labor cost", now));
        }
        return issues;
    }

    /** "LECH CN": the same person has paid hours at more than one branch on the same day. */
    private List<DataValidationIssue> findBranchConflicts(PayrollPeriod period,
            Map<UUID, List<TimesheetEntry>> entriesByLine, List<PayrollLine> lines) {
        Map<UUID, UUID> branchByLine = lines.stream()
                .collect(Collectors.toMap(PayrollLine::getPayrollLineId, PayrollLine::getBranchId));
        Map<UUID, EmploymentAssignment> byAssignment = new HashMap<>();
        for (PayrollLine line : lines) {
            assignmentRepository.findById(line.getAssignmentId()).ifPresent(a -> byAssignment.put(line.getPayrollLineId(), a));
        }

        Map<String, Set<UUID>> branchesByStaffAndDate = new HashMap<>();
        for (var entry : entriesByLine.entrySet()) {
            EmploymentAssignment assignment = byAssignment.get(entry.getKey());
            UUID branchId = branchByLine.get(entry.getKey());
            if (assignment == null || branchId == null) {
                continue;
            }
            for (TimesheetEntry timesheetEntry : entry.getValue()) {
                if (timesheetEntry.getPaidHours() == null || timesheetEntry.getPaidHours().signum() <= 0) {
                    continue;
                }
                String key = assignment.getStaffId() + "|" + timesheetEntry.getWorkDate();
                branchesByStaffAndDate.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(branchId);
            }
        }

        List<DataValidationIssue> issues = new ArrayList<>();
        Instant now = Instant.now();
        for (var entry : branchesByStaffAndDate.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(issue(period, IssueCode.BRANCH_CONFLICT_SAME_DAY, IssueSeverity.WARNING,
                        entry.getKey(), "This person has paid hours at more than one branch on the same day", now));
            }
        }
        return issues;
    }

    private DataValidationIssue issue(PayrollPeriod period, IssueCode code, IssueSeverity severity, String entityRef,
            String message, Instant now) {
        DataValidationIssue issue = new DataValidationIssue();
        issue.setDataValidationIssueId(UUID.randomUUID());
        issue.setBusinessId(period.getBusinessId());
        issue.setPeriodId(period.getPayrollPeriodId());
        issue.setIssueCode(code);
        issue.setSeverity(severity);
        issue.setEntityRef(entityRef);
        issue.setMessage(message);
        issue.setDetectedAt(now);
        return issue;
    }

    private BigDecimal rate(BigDecimal amount, BigDecimal divisor) {
        if (amount == null) {
            return null;
        }
        return amount.divide(nonZero(divisor), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nonZero(BigDecimal value) {
        return value == null || value.signum() == 0 ? BigDecimal.ONE : value;
    }
}

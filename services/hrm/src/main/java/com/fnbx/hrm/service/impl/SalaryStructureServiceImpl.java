package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.SalaryPreviewRequest;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse.InsuranceCost;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse.PersonalAllowances;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse.SchemeCost;
import com.fnbx.hrm.entity.EmployeeAllowance;
import com.fnbx.hrm.entity.InsuranceScheme;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmployeeAllowanceRepository;
import com.fnbx.hrm.repository.InsuranceSchemeRepository;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.service.SalaryStructureService;
import com.fnbx.hrm.service.salary.InsuranceBreakdown;
import com.fnbx.hrm.service.salary.InsuranceEstimator;
import com.fnbx.hrm.service.salary.SalaryStructure;
import com.fnbx.hrm.service.salary.SalaryStructureCalculator;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryStructureServiceImpl implements SalaryStructureService {

    private final SalaryStructureCalculator calculator;
    private final InsuranceEstimator insuranceEstimator;
    private final InsuranceSchemeRepository schemeRepository;
    private final PayrollConfigRepository configRepository;
    private final EmployeeAllowanceRepository allowanceRepository;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public SalaryPreviewResponse preview(SalaryPreviewRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        LocalDate asOf = request.asOf() == null ? LocalDate.now() : request.asOf();
        PayrollConfig config = configRepository.findCurrent(asOf).orElseThrow(PayrollExceptions::resourceNotFound);
        List<InsuranceScheme> schemes = schemeRepository.findAllEffective(asOf);

        SalaryStructure structure = calculator.split(request.agreedMonthlySalary(), request.insuranceBase(), request.insured());
        BigDecimal dailyRate = structure.fixedTotal().divide(config.getStandardDaysPerMonth(), 0, RoundingMode.HALF_UP);

        InsuranceCost after = insuranceCost(request, structure.baseSalary(), schemes);
        InsuranceCost before = insuranceCost(request, request.agreedMonthlySalary(), schemes);
        BigDecimal responsibility = orZero(request.responsibilityAllowance());
        PersonalAllowances personal = personalAllowancesOf(request.staffId(), asOf);
        BigDecimal fixedTotal = structure.fixedTotal().add(responsibility).add(personal.total());
        return new SalaryPreviewResponse(structure.baseSalary(), structure.supplementAllowance(), responsibility, personal,
                fixedTotal, dailyRate, after, before);
    }

    private PersonalAllowances personalAllowancesOf(UUID staffId, LocalDate asOf) {
        EmployeeAllowance allowance = staffId == null ? null : allowanceRepository.findEffective(staffId, asOf).orElse(null);
        if (allowance == null) {
            return new PersonalAllowances(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new PersonalAllowances(orZero(allowance.getLunchAllowance()), orZero(allowance.getHousingAllowance()),
                orZero(allowance.getPhoneAllowance()), orZero(allowance.getFuelAllowance()));
    }

    private InsuranceCost insuranceCost(SalaryPreviewRequest request, BigDecimal baseSalary, List<InsuranceScheme> schemes) {
        if (!request.insured()) {
            return new InsuranceCost(BigDecimal.ZERO, List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal insuranceBase = baseSalary
                .add(orZero(request.kpiAllowance()))
                .add(orZero(request.responsibilityAllowance()));
        InsuranceBreakdown breakdown = insuranceEstimator.estimate(insuranceBase, schemes);
        List<SchemeCost> costs = breakdown.schemes().stream()
                .map(s -> new SchemeCost(s.schemeCode(), s.employeeAmount(), s.employerAmount()))
                .toList();
        return new InsuranceCost(insuranceBase, costs, breakdown.employeeTotal(), breakdown.employerTotal());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

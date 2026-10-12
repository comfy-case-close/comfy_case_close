package com.fnbx.hrm.service.contract;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmploymentAssignmentWriter {

    private final EmploymentAssignmentRepository assignmentRepository;
    private final ContractPayResolver payResolver;
    private final ContractNumberGenerator numberGenerator;

    public EmploymentAssignment create(UUID staffId, EmploymentAssignmentRequest request) {
        EmploymentAssignment assignment = new EmploymentAssignment();
        assignment.setEmploymentAssignmentId(UUID.randomUUID());
        assignment.setBusinessId(TenantContext.current().businessId());
        assignment.setStaffId(staffId);
        assignment.setContractNo(numberGenerator.next(request.getEffectiveFrom()));
        return save(assignment, request);
    }

    public EmploymentAssignment update(EmploymentAssignment assignment, EmploymentAssignmentRequest request) {
        return save(assignment, request);
    }

    public boolean changesPayrollInputs(EmploymentAssignment assignment, EmploymentAssignmentRequest request) {
        ContractPay pay = payResolver.resolve(request);
        return !Objects.equals(assignment.getPositionId(), request.getPositionId())
                || !Objects.equals(assignment.getDefaultBranchId(), request.getDefaultBranchId())
                || assignment.getEmploymentType() != request.getEmploymentType()
                || assignment.isInsured() != request.isInsured()
                || assignment.isFixedSalary() != request.isFixedSalary()
                || !Objects.equals(assignment.getEffectiveFrom(), request.getEffectiveFrom())
                || !Objects.equals(assignment.getEffectiveTo(), request.getEffectiveTo())
                || differ(assignment.getMonthlyBaseSalary(), pay.monthlyBaseSalary())
                || differ(assignment.getSupplementAllowance(), pay.supplementAllowance())
                || differ(assignment.getHourlyBaseRate(), pay.hourlyBaseRate())
                || differ(assignment.getKpiAllowance(), request.getKpiAllowance())
                || differ(assignment.getResponsibilityAllowance(), request.getResponsibilityAllowance());
    }

    private boolean differ(BigDecimal stored, BigDecimal requested) {
        if (stored == null || requested == null) {
            return stored != requested;
        }
        return stored.compareTo(requested) != 0;
    }

    private EmploymentAssignment save(EmploymentAssignment assignment, EmploymentAssignmentRequest request) {
        apply(assignment, request);
        try {
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw PayrollExceptions.assignmentOverlap();
        }
    }

    private void apply(EmploymentAssignment assignment, EmploymentAssignmentRequest request) {
        ContractPay pay = payResolver.resolve(request);
        assignment.setPositionId(request.getPositionId());
        assignment.setDefaultBranchId(request.getDefaultBranchId());
        assignment.setEmploymentType(request.getEmploymentType());
        assignment.setMonthlyBaseSalary(pay.monthlyBaseSalary());
        assignment.setSupplementAllowance(pay.supplementAllowance());
        assignment.setHourlyBaseRate(pay.hourlyBaseRate());
        assignment.setKpiAllowance(request.getKpiAllowance());
        assignment.setResponsibilityAllowance(request.getResponsibilityAllowance());
        assignment.setInsured(request.isInsured());
        assignment.setFixedSalary(request.isFixedSalary());
        assignment.setJobLevel(request.getJobLevel());
        assignment.setContractKind(request.getContractKind());
        assignment.setProbationResult(request.getProbationResult());
        assignment.setEffectiveFrom(request.getEffectiveFrom());
        assignment.setEffectiveTo(request.getEffectiveTo());
        assignment.setNote(request.getNote());
    }
}

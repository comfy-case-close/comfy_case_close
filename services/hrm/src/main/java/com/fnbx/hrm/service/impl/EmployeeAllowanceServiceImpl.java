package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.EmployeeAllowanceRequest;
import com.fnbx.hrm.dto.response.EmployeeAllowanceResponse;
import com.fnbx.hrm.entity.EmployeeAllowance;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.EmployeeMapper;
import com.fnbx.hrm.repository.EmployeeAllowanceRepository;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.service.EmployeeAllowanceService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeAllowanceServiceImpl implements EmployeeAllowanceService {

    private final EmployeeAllowanceRepository allowanceRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final EmployeeMapper mapper;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeAllowanceResponse> listByEmployee(UUID staffId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return allowanceRepository.findByStaffIdOrderByEffectiveFromDesc(staffId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public EmployeeAllowanceResponse create(UUID staffId, EmployeeAllowanceRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        if (!employeeProfileRepository.existsById(staffId)) {
            throw PayrollExceptions.employeeNotFound();
        }
        List<EmployeeAllowance> earlier = allowanceRepository.findByStaffIdOrderByEffectiveFromDesc(staffId);
        if (earlier.stream().anyMatch(existing -> !existing.getEffectiveFrom().isBefore(request.getEffectiveFrom()))) {
            throw PayrollExceptions.invalidField("An allowance already starts on or after that date");
        }
        // A new allowance takes over from its start date: the one it replaces ends the day before.
        List<EmployeeAllowance> replaced = earlier.stream()
                .filter(existing -> existing.getEffectiveTo() == null || !existing.getEffectiveTo().isBefore(request.getEffectiveFrom()))
                .peek(existing -> existing.setEffectiveTo(request.getEffectiveFrom().minusDays(1)))
                .toList();
        allowanceRepository.saveAllAndFlush(replaced);
        EmployeeAllowance allowance = new EmployeeAllowance();
        allowance.setEmployeeAllowanceId(UUID.randomUUID());
        allowance.setBusinessId(TenantContext.current().businessId());
        allowance.setStaffId(staffId);
        allowance.setLunchAllowance(request.getLunchAllowance());
        allowance.setHousingAllowance(request.getHousingAllowance());
        allowance.setPhoneAllowance(request.getPhoneAllowance());
        allowance.setFuelAllowance(request.getFuelAllowance());
        allowance.setEffectiveFrom(request.getEffectiveFrom());
        allowance.setEffectiveTo(request.getEffectiveTo());
        try {
            allowanceRepository.saveAndFlush(allowance);
        } catch (DataIntegrityViolationException ex) {
            throw PayrollExceptions.assignmentOverlap();
        }
        return mapper.toResponse(allowance);
    }
}

package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.DepartmentRequest;
import com.fnbx.hrm.dto.response.DepartmentResponse;
import com.fnbx.hrm.entity.Department;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.DepartmentRepository;
import com.fnbx.hrm.service.DepartmentService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        access.requireAnyBusiness(Permission.HR_RECORD_READ, Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return departmentRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        Department department = new Department();
        department.setDepartmentId(UUID.randomUUID());
        department.setBusinessId(TenantContext.current().businessId());
        apply(department, request);
        return mapper.toResponse(departmentRepository.save(department));
    }

    @Override
    @Transactional
    public DepartmentResponse update(UUID departmentId, DepartmentRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        Department department = require(departmentId);
        apply(department, request);
        return mapper.toResponse(departmentRepository.save(department));
    }

    @Override
    @Transactional
    public void deactivate(UUID departmentId) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        Department department = require(departmentId);
        department.setActive(false);
        departmentRepository.save(department);
    }

    private Department require(UUID departmentId) {
        return departmentRepository.findById(departmentId).orElseThrow(PayrollExceptions::resourceNotFound);
    }

    private void apply(Department department, DepartmentRequest request) {
        department.setDepartmentCode(request.getDepartmentCode());
        department.setDepartmentName(request.getDepartmentName());
        department.setDisplayOrder(request.getDisplayOrder());
        department.setActive(request.isActive());
    }
}

package com.fnbx.identity.service.impl;

import com.fnbx.shared.logging.LogOperation;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.fnbx.identity.dto.request.DepartmentRequest;
import com.fnbx.identity.dto.response.DepartmentResponse;
import com.fnbx.identity.entity.DepartmentProfile;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.DepartmentRepository;
import com.fnbx.identity.service.DepartmentService;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    /** Whoever sets up HR or payroll needs to see the catalogue; only DEPARTMENT_MANAGE changes it. */
    private static final Set<Permission> READERS = EnumSet.of(Permission.DEPARTMENT_MANAGE,
            Permission.HR_RECORD_READ, Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);

    private final DepartmentRepository departments;
    private final BranchAccessGuard permissions;
    private final TenantTransactions transactions;

    public DepartmentServiceImpl(DepartmentRepository departments, BranchAccessGuard permissions,
            TenantTransactions transactions) {
        this.departments = departments; this.permissions = permissions; this.transactions = transactions;
    }

    @Override
    @LogOperation
    public List<DepartmentResponse> list() {
        Set<Permission> held = permissions.effective(null);
        if (READERS.stream().noneMatch(held::contains)) throw new AccessDeniedException("Business permission denied");
        return transactions.inCurrentTenant(() -> departments.findAll().stream().map(DepartmentServiceImpl::response).toList());
    }

    @Override
    @LogOperation(write = true)
    public DepartmentResponse create(DepartmentRequest request) {
        permissions.requireBusiness(Permission.DEPARTMENT_MANAGE);
        try {
            return transactions.inCurrentTenant(() -> {
                UUID departmentId = departments.insert(TenantContext.current().businessId(), code(request),
                        request.departmentName().strip(), displayOrder(request), active(request));
                return response(departments.find(departmentId).orElseThrow(OnboardingExceptions::departmentNotFound));
            });
        } catch (DuplicateKeyException duplicate) {
            throw OnboardingExceptions.departmentCodeTaken();
        }
    }

    @Override
    @LogOperation(write = true)
    public DepartmentResponse update(UUID departmentId, DepartmentRequest request) {
        permissions.requireBusiness(Permission.DEPARTMENT_MANAGE);
        try {
            return transactions.inCurrentTenant(() -> {
                departments.find(departmentId).orElseThrow(OnboardingExceptions::departmentNotFound);
                departments.update(departmentId, code(request), request.departmentName().strip(),
                        displayOrder(request), active(request));
                return response(departments.find(departmentId).orElseThrow(OnboardingExceptions::departmentNotFound));
            });
        } catch (DuplicateKeyException duplicate) {
            throw OnboardingExceptions.departmentCodeTaken();
        }
    }

    @Override
    @LogOperation(write = true)
    public void deactivate(UUID departmentId) {
        permissions.requireBusiness(Permission.DEPARTMENT_MANAGE);
        transactions.inCurrentTenant(() -> {
            departments.find(departmentId).orElseThrow(OnboardingExceptions::departmentNotFound);
            departments.deactivate(departmentId);
            return null;
        });
    }

    /** identity.department only accepts upper case codes (CHECK department_code = upper(department_code)). */
    private static String code(DepartmentRequest request) {
        return request.departmentCode().strip().toUpperCase(Locale.ROOT);
    }

    private static int displayOrder(DepartmentRequest request) {
        return request.displayOrder() == null ? 0 : request.displayOrder();
    }

    private static boolean active(DepartmentRequest request) {
        return request.active() == null || request.active();
    }

    private static DepartmentResponse response(DepartmentProfile department) {
        return new DepartmentResponse(department.departmentId(), department.departmentCode(),
                department.departmentName(), department.displayOrder(), department.active());
    }
}

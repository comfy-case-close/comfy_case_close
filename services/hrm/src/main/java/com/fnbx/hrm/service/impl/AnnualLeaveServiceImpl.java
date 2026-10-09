package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.AnnualLeaveQuotaRequest;
import com.fnbx.hrm.dto.response.AnnualLeaveBalanceResponse;
import com.fnbx.hrm.dto.response.AnnualLeaveQuotaResponse;
import com.fnbx.hrm.entity.EmployeeLeaveQuota;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.EmployeeMapper;
import com.fnbx.hrm.repository.EmployeeLeaveQuotaRepository;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.service.AnnualLeaveBalanceCalculator;
import com.fnbx.hrm.service.AnnualLeaveService;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnualLeaveServiceImpl implements AnnualLeaveService {

    private final EmployeeLeaveQuotaRepository quotaRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final AnnualLeaveBalanceCalculator balanceCalculator;
    private final EmployeeMapper mapper;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public AnnualLeaveQuotaResponse getQuota(UUID staffId, short year) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return quotaRepository.findByStaffIdAndLeaveYear(staffId, year)
                .map(mapper::toResponse)
                .orElseThrow(PayrollExceptions::leaveQuotaNotFound);
    }

    @Override
    @Transactional
    public AnnualLeaveQuotaResponse setQuota(UUID staffId, short year, AnnualLeaveQuotaRequest request) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        if (!employeeProfileRepository.existsById(staffId)) {
            throw PayrollExceptions.employeeNotFound();
        }
        EmployeeLeaveQuota quota = quotaRepository.findByStaffIdAndLeaveYear(staffId, year).orElseGet(() -> {
            EmployeeLeaveQuota created = new EmployeeLeaveQuota();
            created.setEmployeeLeaveQuotaId(UUID.randomUUID());
            created.setBusinessId(TenantContext.current().businessId());
            created.setStaffId(staffId);
            created.setLeaveYear(year);
            return created;
        });
        quota.setQuotaDays(request.getQuotaDays());
        quota.setOpeningUsedDays(request.getOpeningUsedDays());
        return mapper.toResponse(quotaRepository.save(quota));
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualLeaveBalanceResponse getBalance(UUID staffId, short year) {
        branchAccess.requireBusiness(Permission.HR_RECORD_READ);
        return balanceCalculator.calculate(staffId, year);
    }
}

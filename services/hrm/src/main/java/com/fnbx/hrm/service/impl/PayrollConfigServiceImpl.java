package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.PayrollConfigRequest;
import com.fnbx.hrm.dto.response.PayrollConfigResponse;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.service.PayrollConfigService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollConfigServiceImpl implements PayrollConfigService {

    private final PayrollConfigRepository payrollConfigRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<PayrollConfigResponse> list() {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return payrollConfigRepository.findAllByOrderByEffectiveFromDesc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollConfigResponse getCurrent() {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return payrollConfigRepository.findCurrent(LocalDate.now())
                .map(mapper::toResponse)
                .orElseThrow(PayrollExceptions::resourceNotFound);
    }

    @Override
    @Transactional
    public PayrollConfigResponse create(PayrollConfigRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        PayrollConfig config = new PayrollConfig();
        config.setPayrollConfigId(UUID.randomUUID());
        config.setBusinessId(TenantContext.current().businessId());
        config.setEffectiveFrom(request.getEffectiveFrom());
        config.setStandardDaysPerMonth(request.getStandardDaysPerMonth());
        config.setStandardHoursPerDay(request.getStandardHoursPerDay());
        config.setOvertimeMultiplier(request.getOvertimeMultiplier());
        config.setWeekendMultiplier(request.getWeekendMultiplier());
        config.setPayPeriodStartDay(request.getPayPeriodStartDay());
        config.setPeriodMode(request.getPeriodMode());
        config.setPayslipEmailSubjectTemplate(request.getPayslipEmailSubjectTemplate());
        config.setPayslipEmailBodyTemplate(request.getPayslipEmailBodyTemplate());
        return mapper.toResponse(payrollConfigRepository.saveAndFlush(config));
    }
}

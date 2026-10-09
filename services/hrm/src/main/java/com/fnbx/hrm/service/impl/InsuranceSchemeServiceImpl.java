package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.InsuranceSchemeRequest;
import com.fnbx.hrm.dto.response.InsuranceSchemeResponse;
import com.fnbx.hrm.entity.InsuranceScheme;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.InsuranceSchemeRepository;
import com.fnbx.hrm.service.InsuranceSchemeService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.hrm.exception.PayrollExceptions;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsuranceSchemeServiceImpl implements InsuranceSchemeService {

    private final InsuranceSchemeRepository insuranceSchemeRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<InsuranceSchemeResponse> list() {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return insuranceSchemeRepository.findAllByOrderBySchemeCodeAscEffectiveFromDesc().stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public InsuranceSchemeResponse create(InsuranceSchemeRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        List<InsuranceScheme> sameCode = insuranceSchemeRepository.findAll().stream()
                .filter(existing -> existing.getSchemeCode().equals(request.getSchemeCode())).toList();
        if (sameCode.stream().anyMatch(existing -> !existing.getEffectiveFrom().isBefore(request.getEffectiveFrom()))) {
            throw PayrollExceptions.invalidField("A scheme with this code already starts on or after that date");
        }
        // A new rate takes over from its start date: the one it replaces ends the day before.
        sameCode.stream()
                .filter(existing -> existing.getEffectiveTo() == null || !existing.getEffectiveTo().isBefore(request.getEffectiveFrom()))
                .forEach(existing -> existing.setEffectiveTo(request.getEffectiveFrom().minusDays(1)));
        InsuranceScheme scheme = new InsuranceScheme();
        scheme.setInsuranceSchemeId(UUID.randomUUID());
        scheme.setBusinessId(TenantContext.current().businessId());
        scheme.setSchemeCode(request.getSchemeCode());
        scheme.setSchemeName(request.getSchemeName());
        scheme.setEmployerRate(request.getEmployerRate());
        scheme.setEmployeeRate(request.getEmployeeRate());
        scheme.setEffectiveFrom(request.getEffectiveFrom());
        scheme.setEffectiveTo(request.getEffectiveTo());
        return mapper.toResponse(insuranceSchemeRepository.save(scheme));
    }
}

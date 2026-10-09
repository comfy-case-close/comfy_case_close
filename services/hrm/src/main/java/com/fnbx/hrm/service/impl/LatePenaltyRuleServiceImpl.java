package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.LatePenaltyRuleRequest;
import com.fnbx.hrm.dto.response.LatePenaltyRuleResponse;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.LatePenaltyRuleRepository;
import com.fnbx.hrm.service.LatePenaltyRuleService;
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
public class LatePenaltyRuleServiceImpl implements LatePenaltyRuleService {

    private final LatePenaltyRuleRepository latePenaltyRuleRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<LatePenaltyRuleResponse> list() {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return latePenaltyRuleRepository.findAllByOrderByEffectiveFromDesc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public LatePenaltyRuleResponse create(LatePenaltyRuleRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        List<LatePenaltyRule> sameCode = latePenaltyRuleRepository.findAllByOrderByEffectiveFromDesc().stream()
                .filter(existing -> existing.getRuleCode().equals(request.getRuleCode())).toList();
        if (sameCode.stream().anyMatch(existing -> !existing.getEffectiveFrom().isBefore(request.getEffectiveFrom()))) {
            throw PayrollExceptions.invalidField("A rule with this code already starts on or after that date");
        }
        // A new version takes over from its start date: the one it replaces ends the day before.
        sameCode.stream()
                .filter(existing -> existing.getEffectiveTo() == null || !existing.getEffectiveTo().isBefore(request.getEffectiveFrom()))
                .forEach(existing -> existing.setEffectiveTo(request.getEffectiveFrom().minusDays(1)));
        LatePenaltyRule rule = new LatePenaltyRule();
        rule.setLatePenaltyRuleId(UUID.randomUUID());
        rule.setBusinessId(TenantContext.current().businessId());
        rule.setRuleCode(request.getRuleCode());
        rule.setDescription(request.getDescription());
        rule.setDeductHours(request.getDeductHours());
        rule.setDeductRatio(request.getDeductRatio());
        rule.setVoidsShift(request.isVoidsShift());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        return mapper.toResponse(latePenaltyRuleRepository.save(rule));
    }
}

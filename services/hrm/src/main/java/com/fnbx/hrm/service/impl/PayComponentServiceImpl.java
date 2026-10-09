package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.PayComponentUpdateRequest;
import com.fnbx.hrm.dto.response.PayComponentResponse;
import com.fnbx.hrm.entity.PayComponent;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.mapper.PayrollSettingMapper;
import com.fnbx.hrm.repository.PayComponentRepository;
import com.fnbx.hrm.service.PayComponentService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayComponentServiceImpl implements PayComponentService {

    private final PayComponentRepository payComponentRepository;
    private final PayrollSettingMapper mapper;
    private final BranchAccessGuard branchAccess;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<PayComponentResponse> list() {
        access.requireAnyBusiness(Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE);
        return payComponentRepository.findAllByOrderByDisplayOrder().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PayComponentResponse update(UUID payComponentId, PayComponentUpdateRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        PayComponent component = payComponentRepository.findById(payComponentId)
                .orElseThrow(PayrollExceptions::resourceNotFound);
        component.setComponentName(request.getComponentName());
        component.setDisplayOrder(request.getDisplayOrder());
        return mapper.toResponse(payComponentRepository.save(component));
    }
}

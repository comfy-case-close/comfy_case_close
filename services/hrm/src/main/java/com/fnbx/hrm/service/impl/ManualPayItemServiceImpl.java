package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.ManualPayItemRequest;
import com.fnbx.hrm.dto.response.ManualPayItemResponse;
import com.fnbx.hrm.entity.PayComponent;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollLineItem;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayComponentRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.service.ManualPayItemService;
import com.fnbx.hrm.security.PayrollAccess;
import com.fnbx.shared.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualPayItemServiceImpl implements ManualPayItemService {

    private final PayrollLineRepository lineRepository;
    private final PayrollLineItemRepository itemRepository;
    private final PayComponentRepository componentRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollAccess access;

    @Override
    @Transactional(readOnly = true)
    public List<ManualPayItemResponse> list(UUID lineId) {
        access.requireAllBusiness(Permission.PAYROLL_PROCESS, Permission.HR_RECORD_READ);
        Map<UUID, PayComponent> componentsById = componentRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(PayComponent::getPayComponentId, c -> c));
        return itemRepository.findManualItems(lineId).stream()
                .map(item -> toResponse(item, componentsById.get(item.getComponentId())))
                .toList();
    }

    @Override
    @Transactional
    public ManualPayItemResponse create(UUID lineId, ManualPayItemRequest request) {
        access.requireAllBusiness(Permission.PAYROLL_PROCESS, Permission.HR_RECORD_READ);
        PayrollLine line = lineRepository.findById(lineId).orElseThrow(PayrollExceptions::payrollLineNotFound);
        requireDraftPeriod(line.getPeriodId());
        PayComponent component = requireManualComponent(request);

        PayrollLineItem item = new PayrollLineItem();
        item.setPayrollLineItemId(UUID.randomUUID());
        item.setBusinessId(line.getBusinessId());
        item.setPayrollLineId(lineId);
        item.setComponentId(component.getPayComponentId());
        item.setAmount(request.getAmount());
        item.setNote(request.getNote());
        itemRepository.save(item);
        return toResponse(item, component);
    }

    @Override
    @Transactional
    public ManualPayItemResponse update(UUID itemId, ManualPayItemRequest request) {
        access.requireAllBusiness(Permission.PAYROLL_PROCESS, Permission.HR_RECORD_READ);
        PayrollLineItem item = itemRepository.findById(itemId).orElseThrow(PayrollExceptions::resourceNotFound);
        PayrollLine line = lineRepository.findById(item.getPayrollLineId()).orElseThrow(PayrollExceptions::payrollLineNotFound);
        requireDraftPeriod(line.getPeriodId());
        PayComponent component = requireManualComponent(request);

        item.setComponentId(component.getPayComponentId());
        item.setAmount(request.getAmount());
        item.setNote(request.getNote());
        itemRepository.save(item);
        return toResponse(item, component);
    }

    @Override
    @Transactional
    public void delete(UUID itemId) {
        access.requireAllBusiness(Permission.PAYROLL_PROCESS, Permission.HR_RECORD_READ);
        PayrollLineItem item = itemRepository.findById(itemId).orElseThrow(PayrollExceptions::resourceNotFound);
        PayrollLine line = lineRepository.findById(item.getPayrollLineId()).orElseThrow(PayrollExceptions::payrollLineNotFound);
        requireDraftPeriod(line.getPeriodId());
        itemRepository.delete(item);
    }

    private PayComponent requireManualComponent(ManualPayItemRequest request) {
        PayComponent component = componentRepository.findByComponentCode(request.getComponentCode())
                .orElseThrow(PayrollExceptions::resourceNotFound);
        if (!component.isManualInput()) {
            throw PayrollExceptions.manualItemNotAllowed("'" + request.getComponentCode() + "' is not a manual item");
        }
        if (!component.isAllowNegative() && request.getAmount().signum() < 0) {
            throw PayrollExceptions.manualItemNotAllowed("This component cannot take a negative amount");
        }
        return component;
    }

    private void requireDraftPeriod(UUID periodId) {
        PayrollPeriod period = periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
        if (period.getStatus() != PeriodStatus.DRAFT) {
            throw PayrollExceptions.periodNotDraft("Manual items can only be edited while the period is a draft");
        }
    }

    private ManualPayItemResponse toResponse(PayrollLineItem item, PayComponent component) {
        return ManualPayItemResponse.builder()
                .payrollLineItemId(item.getPayrollLineItemId())
                .payrollLineId(item.getPayrollLineId())
                .componentCode(component == null ? null : component.getComponentCode())
                .amount(item.getAmount())
                .note(item.getNote())
                .build();
    }
}

package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.CreateContractTemplateRequest;
import com.fnbx.hrm.dto.request.SetTemplateActiveRequest;
import com.fnbx.hrm.dto.response.ContractTemplateResponse;
import com.fnbx.hrm.dto.response.PlaceholderResponse;
import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.repository.ContractTemplateRepository;
import com.fnbx.hrm.service.ContractTemplateService;
import com.fnbx.hrm.service.contractdoc.ContractPdfRenderer;
import com.fnbx.hrm.service.contractdoc.ContractPlaceholder;
import com.fnbx.hrm.service.contractdoc.PlaceholderRenderer;
import com.fnbx.hrm.service.contractdoc.TemplateBodyValidator;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final TemplateBodyValidator bodyValidator;
    private final PlaceholderRenderer placeholderRenderer;
    private final ContractPdfRenderer pdfRenderer;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public List<ContractTemplateResponse> list() {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return templateRepository.findAllByOrderByNameAscVersionDesc().stream().map(this::toResponse).toList();
    }

    @Override
    public List<PlaceholderResponse> placeholders() {
        return ContractPlaceholder.all().stream()
                .map(placeholder -> new PlaceholderResponse(placeholder.key(), placeholder.description(), placeholder.sample()))
                .toList();
    }

    @Override
    @Transactional
    public ContractTemplateResponse create(CreateContractTemplateRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        bodyValidator.requireWellFormed(request.bodyHtml());
        bodyValidator.requireKnownPlaceholders(request.bodyHtml());
        ContractTemplate template = new ContractTemplate();
        template.setContractTemplateId(UUID.randomUUID());
        template.setBusinessId(TenantContext.current().businessId());
        template.setPositionId(request.positionId());
        template.setName(request.name());
        template.setVersion(templateRepository.latestVersion(request.positionId()) + 1);
        template.setBodyHtml(request.bodyHtml());
        template.setActive(true);
        template.setEffectiveFrom(request.effectiveFrom());
        template.setCreatedBy(TenantContext.current().userId());
        template.setCreatedAt(Instant.now());
        return toResponse(templateRepository.saveAndFlush(template));
    }

    @Override
    @Transactional
    public ContractTemplateResponse setActive(UUID templateId, SetTemplateActiveRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        ContractTemplate template = require(templateId);
        template.setActive(request.active());
        return toResponse(templateRepository.saveAndFlush(template));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] preview(UUID templateId) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        Map<ContractPlaceholder, String> sample = new EnumMap<>(ContractPlaceholder.class);
        ContractPlaceholder.all().forEach(placeholder -> sample.put(placeholder, placeholder.sample()));
        return pdfRenderer.render(placeholderRenderer.render(require(templateId).getBodyHtml(), sample));
    }

    private ContractTemplate require(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND));
    }

    private ContractTemplateResponse toResponse(ContractTemplate template) {
        return new ContractTemplateResponse(template.getContractTemplateId(), template.getPositionId(), template.getName(),
                template.getVersion(), template.isActive(), template.getEffectiveFrom(), template.getBodyHtml());
    }
}

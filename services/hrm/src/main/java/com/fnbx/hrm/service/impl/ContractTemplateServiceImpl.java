package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.CreateContractTemplateRequest;
import com.fnbx.hrm.dto.request.ImportContractTemplateRequest;
import com.fnbx.hrm.dto.request.SetTemplateActiveRequest;
import com.fnbx.hrm.dto.response.ContractTemplateResponse;
import com.fnbx.hrm.dto.response.PlaceholderResponse;
import com.fnbx.hrm.dto.response.StandardTemplateInstallResponse;
import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.enums.ContractTemplateKind;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.ContractTemplateRepository;
import com.fnbx.hrm.service.ContractTemplateService;
import com.fnbx.hrm.service.contractdoc.ContractPdfRenderer;
import com.fnbx.hrm.service.contractdoc.ContractPlaceholder;
import com.fnbx.hrm.service.contractdoc.ContractTemplateBodyBuilder;
import com.fnbx.hrm.service.contractdoc.PlaceholderRenderer;
import com.fnbx.hrm.service.contractdoc.StandardBlanks;
import com.fnbx.hrm.service.contractdoc.StandardTemplateInstaller;
import com.fnbx.hrm.service.contractdoc.TemplateBodyValidator;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
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
    private final com.fnbx.hrm.repository.PositionProfileRepository positionProfileRepository;
    private final TemplateBodyValidator bodyValidator;
    private final StandardTemplateInstaller standardInstaller;
    private final ContractTemplateBodyBuilder bodyBuilder;
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
        return register(request.positionId(), kindOrDefault(request.kind()), request.name(), request.bodyHtml(), request.effectiveFrom());
    }

    @Override
    @Transactional
    public ContractTemplateResponse importDocx(ImportContractTemplateRequest request, String fileName, InputStream docx) {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        ContractTemplateKind kind = kindOrDefault(request.kind());
        String body = bodyBuilder.fromDocx(docx, StandardBlanks.rewriterFor(kind), fileName);
        return register(request.positionId(), kind, request.name(), body, request.effectiveFrom());
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

    @Override
    @Transactional
    public List<StandardTemplateInstallResponse> installStandard() {
        branchAccess.requireBusiness(Permission.PAYROLL_CONFIG_WRITE);
        return standardInstaller.install();
    }

    private ContractTemplateKind kindOrDefault(ContractTemplateKind kind) {
        return kind == null ? ContractTemplateKind.LABOR_CONTRACT : kind;
    }

    private ContractTemplateResponse register(UUID positionId, ContractTemplateKind kind, String name, String bodyHtml, LocalDate effectiveFrom) {
        if (positionId != null && !positionProfileRepository.existsById(positionId)) {
            throw PayrollExceptions.invalidField("This position has no payroll profile yet: assign it a department in Setup > Departments and positions first");
        }
        ContractTemplate template = new ContractTemplate();
        template.setContractTemplateId(UUID.randomUUID());
        template.setBusinessId(TenantContext.current().businessId());
        template.setPositionId(positionId);
        template.setKind(kind);
        template.setName(name);
        template.setVersion(templateRepository.latestVersion(positionId, kind) + 1);
        template.setBodyHtml(bodyHtml);
        template.setActive(true);
        template.setEffectiveFrom(effectiveFrom);
        template.setCreatedBy(TenantContext.current().userId());
        template.setCreatedAt(Instant.now());
        return toResponse(templateRepository.saveAndFlush(template));
    }

    private ContractTemplate require(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND));
    }

    private ContractTemplateResponse toResponse(ContractTemplate template) {
        return new ContractTemplateResponse(template.getContractTemplateId(), template.getPositionId(), template.getName(),
                template.getVersion(), template.isActive(), template.getEffectiveFrom(), template.getBodyHtml(), template.getKind());
    }
}

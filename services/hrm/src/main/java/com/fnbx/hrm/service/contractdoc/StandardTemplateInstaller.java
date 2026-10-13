package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.dto.response.StandardTemplateInstallResponse;
import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.repository.ContractTemplateRepository;
import com.fnbx.hrm.repository.PositionProfileRepository;
import com.fnbx.hrm.service.contractimport.ImportLookups;
import com.fnbx.identity.entity.StaffPosition;
import com.fnbx.shared.tenant.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StandardTemplateInstaller {

    private final ImportLookups lookups;
    private final PositionProfileRepository positionProfileRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractTemplateBodyBuilder bodyBuilder;

    public List<StandardTemplateInstallResponse> install() {
        return StandardContractTemplate.all().stream().map(this::installOne).toList();
    }

    public String bodyOf(StandardContractTemplate standard) {
        try (InputStream docx = standard.open()) {
            return bodyBuilder.fromDocx(docx, standard.blankRewriter(), standard.file());
        } catch (IOException ex) {
            throw new IllegalStateException("The bundled template " + standard.file() + " cannot be read", ex);
        }
    }

    private StandardTemplateInstallResponse installOne(StandardContractTemplate standard) {
        try {
            Optional<StaffPosition> position = lookups.positionByCode(standard.positionCode());
            if (position.isEmpty()) {
                return result(standard, InstallStatus.SKIPPED, "This business has no active position with code " + standard.positionCode());
            }
            UUID positionId = position.get().getPositionId();
            if (!positionProfileRepository.existsById(positionId)) {
                return result(standard, InstallStatus.SKIPPED, "The position has no payroll profile: assign it a department in Setup first");
            }
            return installVersion(standard, positionId, bodyOf(standard));
        } catch (RuntimeException ex) {
            log.warn("Installing the standard template {} failed", standard.file(), ex);
            return result(standard, InstallStatus.FAILED, ex.getMessage());
        }
    }

    private StandardTemplateInstallResponse installVersion(StandardContractTemplate standard, UUID positionId, String body) {
        Optional<ContractTemplate> latest = templateRepository.findVersions(positionId, standard.kind()).stream().findFirst();
        if (latest.filter(template -> template.isActive() && template.getBodyHtml().equals(body)).isPresent()) {
            return result(standard, InstallStatus.UNCHANGED, "Version " + latest.get().getVersion() + " already has this text");
        }
        int version = latest.map(template -> template.getVersion() + 1).orElse(1);
        templateRepository.saveAndFlush(newTemplate(standard, positionId, body, version));
        return result(standard, InstallStatus.INSTALLED, "Version " + version);
    }

    private ContractTemplate newTemplate(StandardContractTemplate standard, UUID positionId, String body, int version) {
        ContractTemplate template = new ContractTemplate();
        template.setContractTemplateId(UUID.randomUUID());
        template.setBusinessId(TenantContext.current().businessId());
        template.setPositionId(positionId);
        template.setKind(standard.kind());
        template.setName(standard.templateName());
        template.setVersion(version);
        template.setBodyHtml(body);
        template.setActive(true);
        template.setEffectiveFrom(LocalDate.now());
        template.setCreatedBy(TenantContext.current().userId());
        template.setCreatedAt(Instant.now());
        return template;
    }

    private StandardTemplateInstallResponse result(StandardContractTemplate standard, InstallStatus status, String message) {
        return new StandardTemplateInstallResponse(standard.templateName(), standard.positionCode(), standard.kind().name(), status.name(), message);
    }

    private enum InstallStatus {
        INSTALLED,
        UNCHANGED,
        SKIPPED,
        FAILED
    }
}

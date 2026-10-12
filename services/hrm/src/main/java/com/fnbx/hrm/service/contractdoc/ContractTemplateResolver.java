package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractTemplateKind;
import com.fnbx.hrm.repository.ContractTemplateRepository;
import com.fnbx.identity.entity.StaffPosition;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractTemplateResolver {

    private final ContractTemplateRepository templateRepository;
    private final StandardTemplateInstaller standardTemplates;
    private final EntityManager entityManager;

    public ContractTemplate resolve(EmploymentAssignment assignment) {
        if (!ContractPrintability.isPrintable(assignment)) {
            throw new AppException(ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND, "Part-time and service contracts are not printed");
        }
        ContractTemplateKind kind = kindOf(assignment);
        return storedTemplate(assignment, kind)
                .or(() -> bundledTemplate(assignment, kind))
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND,
                        "No " + kind + " template for this position"));
    }

    private ContractTemplateKind kindOf(EmploymentAssignment assignment) {
        if (assignment.getContractKind() == null) {
            return ContractTemplateKind.LABOR_CONTRACT;
        }
        return ContractTemplateKind.of(assignment.getContractKind()).orElseThrow(() -> new AppException(
                ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND, "There is no template for a " + assignment.getContractKind() + " contract"));
    }

    private Optional<ContractTemplate> storedTemplate(EmploymentAssignment assignment, ContractTemplateKind kind) {
        LocalDate asOf = assignment.getEffectiveFrom().isAfter(LocalDate.now()) ? assignment.getEffectiveFrom() : LocalDate.now();
        return templateRepository.findActiveForPosition(assignment.getPositionId(), kind, asOf).stream().findFirst()
                .or(() -> templateRepository.findActiveGeneral(kind, asOf).stream().findFirst());
    }

    private Optional<ContractTemplate> bundledTemplate(EmploymentAssignment assignment, ContractTemplateKind kind) {
        String positionCode = entityManager.find(StaffPosition.class, assignment.getPositionId()).getPositionCode();
        return StandardContractTemplate.all().stream()
                .filter(standard -> standard.kind() == kind && standard.positionCode().equals(positionCode))
                .findFirst()
                .map(this::toTransientTemplate);
    }

    private ContractTemplate toTransientTemplate(StandardContractTemplate standard) {
        ContractTemplate template = new ContractTemplate();
        template.setKind(standard.kind());
        template.setName(standard.templateName());
        template.setBodyHtml(standardTemplates.bodyOf(standard));
        template.setActive(true);
        return template;
    }
}

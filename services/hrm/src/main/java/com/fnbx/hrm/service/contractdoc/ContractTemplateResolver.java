package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.ContractTemplate;
import com.fnbx.hrm.repository.ContractTemplateRepository;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractTemplateResolver {

    private final ContractTemplateRepository templateRepository;

    /** The newest active template of the contract's position, else the newest general one. */
    public ContractTemplate resolve(EmploymentAssignment assignment) {
        LocalDate asOf = assignment.getEffectiveFrom().isAfter(LocalDate.now()) ? assignment.getEffectiveFrom() : LocalDate.now();
        return templateRepository.findActiveForPosition(assignment.getPositionId(), asOf).stream().findFirst()
                .or(() -> templateRepository.findActiveGeneral(asOf).stream().findFirst())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_TEMPLATE_NOT_FOUND));
    }
}

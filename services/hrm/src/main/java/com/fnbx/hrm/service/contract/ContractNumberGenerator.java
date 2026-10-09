package com.fnbx.hrm.service.contract;

import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractNumberGenerator {

    private final EmploymentAssignmentRepository assignmentRepository;

    public String next(LocalDate effectiveFrom) {
        return "HD-%d-%02d-%04d".formatted(
                effectiveFrom.getYear(), effectiveFrom.getMonthValue(), assignmentRepository.nextContractSequence());
    }
}

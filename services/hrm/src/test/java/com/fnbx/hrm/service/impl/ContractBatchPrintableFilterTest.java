package com.fnbx.hrm.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.dto.request.GenerateContractsRequest;
import com.fnbx.hrm.entity.ContractDocumentBatch;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.ContractDocumentBatchRepository;
import com.fnbx.hrm.repository.ContractDocumentRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.service.contractdoc.ContractBatchRunner;
import com.fnbx.hrm.service.contractdoc.ContractDocumentGenerator;
import com.fnbx.hrm.service.contractdoc.ContractPdfRenderer;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.security.BranchAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class ContractBatchPrintableFilterTest {

    private final EmploymentAssignmentRepository assignments = mock(EmploymentAssignmentRepository.class);
    private final ContractBatchRunner runner = mock(ContractBatchRunner.class);
    private final ContractDocumentBatchRepository batches = mock(ContractDocumentBatchRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final ContractDocumentServiceImpl service = new ContractDocumentServiceImpl(batches, mock(ContractDocumentRepository.class),
            assignments, runner, mock(ContractPdfRenderer.class), mock(ContractDocumentGenerator.class), transaction,
            mock(BranchAccessGuard.class));
    private final UUID fullTime = UUID.randomUUID();
    private final UUID partTime = UUID.randomUUID();

    private EmploymentAssignment contract(UUID id, EmploymentType type, ContractKind kind) {
        EmploymentAssignment assignment = new EmploymentAssignment();
        assignment.setEmploymentAssignmentId(id);
        assignment.setEmploymentType(type);
        assignment.setContractKind(kind);
        return assignment;
    }

    @Test
    void partTimeContractsAreLeftOutOfTheBatch() {
        when(assignments.findAllById(List.of(fullTime, partTime))).thenReturn(List.of(
                contract(fullTime, EmploymentType.FULLTIME, ContractKind.FIXED_TERM_FT),
                contract(partTime, EmploymentType.PARTTIME, ContractKind.PART_TIME_AGREEMENT)));
        when(transaction.execute(any())).thenAnswer(call -> {
            ContractDocumentBatch batch = new ContractDocumentBatch();
            batch.setContractDocumentBatchId(UUID.randomUUID());
            batch.setStatus(com.fnbx.hrm.enums.RunStatus.RUNNING);
            batch.setTotal(1);
            return batch;
        });

        service.generate(new GenerateContractsRequest(List.of(fullTime, partTime)));

        verify(runner).start(any(), eq(List.of(fullTime)));
    }

    @Test
    void aRequestWithNothingPrintableIsRefused() {
        when(assignments.findAllById(List.of(partTime))).thenReturn(List.of(
                contract(partTime, EmploymentType.PARTTIME, ContractKind.PART_TIME_AGREEMENT)));

        assertThatThrownBy(() -> service.generate(new GenerateContractsRequest(List.of(partTime))))
                .isInstanceOf(AppException.class);
        verify(runner, never()).start(any(), any());
    }
}

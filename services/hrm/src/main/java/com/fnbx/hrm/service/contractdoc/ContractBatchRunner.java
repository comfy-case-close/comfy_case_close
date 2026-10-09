package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.ContractDocumentBatch;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.repository.ContractDocumentBatchRepository;
import com.fnbx.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Generates the documents of a batch on a worker thread, one transaction per contract so one failure never loses the rest. */
@Slf4j
@Component
public class ContractBatchRunner {

    private final TaskExecutor executor;
    private final TransactionTemplate transaction;
    private final ContractDocumentGenerator generator;
    private final ContractDocumentBatchRepository batchRepository;

    public ContractBatchRunner(@Qualifier("contractDocumentExecutor") TaskExecutor executor, TransactionTemplate transaction,
            ContractDocumentGenerator generator, ContractDocumentBatchRepository batchRepository) {
        this.executor = executor;
        this.transaction = transaction;
        this.generator = generator;
        this.batchRepository = batchRepository;
    }

    public void start(UUID batchId, List<UUID> assignmentIds) {
        TenantContext tenant = TenantContext.current();
        executor.execute(() -> TenantContext.runAs(tenant, () -> {
            assignmentIds.forEach(assignmentId -> generateOne(batchId, assignmentId));
            finish(batchId);
            return null;
        }));
    }

    private void generateOne(UUID batchId, UUID assignmentId) {
        try {
            transaction.executeWithoutResult(status -> generator.generate(batchId, assignmentId));
        } catch (RuntimeException ex) {
            log.warn("Contract generation failed: batch={} assignment={}", batchId, assignmentId, ex);
            transaction.executeWithoutResult(status -> generator.recordFailure(batchId, assignmentId, ex.getMessage()));
        }
    }

    private void finish(UUID batchId) {
        transaction.executeWithoutResult(status -> {
            ContractDocumentBatch batch = batchRepository.findById(batchId).orElseThrow();
            batch.setStatus(RunStatus.SUCCEEDED);
            batch.setFinishedAt(Instant.now());
            batchRepository.save(batch);
        });
    }
}

package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ContractDocument;
import com.fnbx.hrm.enums.ContractDocumentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, UUID> {

    List<ContractDocument> findByBatchIdOrderByContractNo(UUID batchId);

    List<ContractDocument> findByEmploymentAssignmentIdOrderByGeneratedAtDesc(UUID employmentAssignmentId);

    long countByBatchIdAndStatus(UUID batchId, ContractDocumentStatus status);
}

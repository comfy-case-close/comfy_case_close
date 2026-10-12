package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ContractDocumentBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractDocumentBatchRepository extends JpaRepository<ContractDocumentBatch, UUID> {
}

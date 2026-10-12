package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContractBatchResponse(UUID batchId, int total, long generated, long failed, String status,
                                    Instant createdAt, Instant finishedAt, List<ContractDocumentResponse> documents) {

    public record ContractDocumentResponse(UUID contractDocumentId, UUID employmentAssignmentId, String contractNo,
                                           String status, String failureReason, Instant generatedAt) {}
}

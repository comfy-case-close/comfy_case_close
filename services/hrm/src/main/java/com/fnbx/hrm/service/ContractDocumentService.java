package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.GenerateContractsRequest;
import com.fnbx.hrm.dto.response.ContractBatchResponse;
import java.util.List;
import java.util.UUID;

public interface ContractDocumentService {

    /** Starts generating one PDF per contract in the background and returns the batch to poll. */
    ContractBatchResponse generate(GenerateContractsRequest request);

    ContractBatchResponse getBatch(UUID batchId);

    byte[] batchPdf(UUID batchId);

    byte[] documentPdf(UUID documentId);

    List<ContractBatchResponse.ContractDocumentResponse> listForAssignment(UUID assignmentId);
}

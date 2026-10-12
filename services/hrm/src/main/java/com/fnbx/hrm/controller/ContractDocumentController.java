package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.GenerateContractsRequest;
import com.fnbx.hrm.dto.response.ContractBatchResponse;
import com.fnbx.hrm.dto.response.ContractBatchResponse.ContractDocumentResponse;
import com.fnbx.hrm.service.ContractDocumentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contract-documents")
@RequiredArgsConstructor
public class ContractDocumentController {

    private final ContractDocumentService documentService;

    @PostMapping("/generate")
    public ResponseEntity<ContractBatchResponse> generate(@Valid @RequestBody GenerateContractsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.generate(request));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ContractBatchResponse> batch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(documentService.getBatch(batchId));
    }

    @GetMapping("/batches/{batchId}/pdf")
    public ResponseEntity<byte[]> batchPdf(@PathVariable UUID batchId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(documentService.batchPdf(batchId));
    }

    @GetMapping("/{documentId}/pdf")
    public ResponseEntity<byte[]> documentPdf(@PathVariable UUID documentId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(documentService.documentPdf(documentId));
    }

    @GetMapping("/by-assignment/{assignmentId}")
    public ResponseEntity<List<ContractDocumentResponse>> byAssignment(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(documentService.listForAssignment(assignmentId));
    }
}

package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.response.ImportJobResponse;
import com.fnbx.hrm.service.ContractImportService;
import com.fnbx.hrm.service.contractimport.CommitMode;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Create many contracts at once from a workbook, one row per contract. */
@RestController
@RequestMapping("/employment-assignments")
@RequiredArgsConstructor
public class ContractImportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ContractImportService importService;

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract-import-template.xlsx\"")
                .contentType(XLSX)
                .body(importService.template());
    }

    @PostMapping("/imports")
    public ResponseEntity<ImportJobResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(importService.validate(file.getOriginalFilename(), file.getInputStream()));
    }

    @GetMapping("/imports/{importJobId}")
    public ResponseEntity<ImportJobResponse> get(@PathVariable UUID importJobId) {
        return ResponseEntity.ok(importService.get(importJobId));
    }

    @PostMapping("/imports/{importJobId}/commit")
    public ResponseEntity<ImportJobResponse> commit(@PathVariable UUID importJobId,
            @RequestParam(defaultValue = "VALID_ONLY") CommitMode commitMode) {
        return ResponseEntity.ok(importService.commit(importJobId, commitMode));
    }
}

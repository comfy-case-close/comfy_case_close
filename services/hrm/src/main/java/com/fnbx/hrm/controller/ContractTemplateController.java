package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.CreateContractTemplateRequest;
import com.fnbx.hrm.dto.request.SetTemplateActiveRequest;
import com.fnbx.hrm.dto.response.ContractTemplateResponse;
import com.fnbx.hrm.dto.response.PlaceholderResponse;
import com.fnbx.hrm.service.ContractTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contract-templates")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<ContractTemplateResponse>> list() {
        return ResponseEntity.ok(templateService.list());
    }

    @GetMapping("/placeholders")
    public ResponseEntity<List<PlaceholderResponse>> placeholders() {
        return ResponseEntity.ok(templateService.placeholders());
    }

    @PostMapping
    public ResponseEntity<ContractTemplateResponse> create(@Valid @RequestBody CreateContractTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(request));
    }

    @PutMapping("/{templateId}/active")
    public ResponseEntity<ContractTemplateResponse> setActive(@PathVariable UUID templateId,
            @RequestBody SetTemplateActiveRequest request) {
        return ResponseEntity.ok(templateService.setActive(templateId, request));
    }

    @PostMapping("/{templateId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable UUID templateId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(templateService.preview(templateId));
    }
}

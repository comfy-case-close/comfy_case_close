package com.fnbx.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fnbx.platform.service.ConfigService;
import com.fnbx.platform.service.MovementKindService;
import com.fnbx.platform.dto.response.ConfigEntryResponse;
import com.fnbx.platform.dto.response.MovementKindResponse;
import com.fnbx.shared.security.BranchHeader;
import com.fnbx.shared.utils.PagedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {
    private final ConfigService service;
    private final MovementKindService movementKinds;

    @GetMapping("/movement-kinds")
    public ResponseEntity<PagedResponse<MovementKindResponse>> movementKinds(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(movementKinds.list(PageRequest.of(page, size)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@RequestHeader(BranchHeader.NAME) UUID branchId) {
        return ResponseEntity.ok(service.get(branchId));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<ConfigEntryResponse>> entries(@RequestHeader(BranchHeader.NAME) UUID branchId) {
        return ResponseEntity.ok(service.entries(branchId));
    }

    @GetMapping("/business/entries")
    public ResponseEntity<List<ConfigEntryResponse>> businessEntries() {
        return ResponseEntity.ok(service.businessEntries());
    }

    /** A full dev-style document or a partial update; omitted keys keep their effective values. */
    @PutMapping
    public ResponseEntity<Map<String, Object>> update(@RequestHeader(BranchHeader.NAME) UUID branchId,
                                                       @RequestBody Map<String, JsonNode> values) {
        return ResponseEntity.ok(service.update(branchId, values));
    }

    @GetMapping("/business")
    public ResponseEntity<Map<String, Object>> getBusiness() {
        return ResponseEntity.ok(service.getBusiness());
    }

    @PutMapping("/business")
    public ResponseEntity<Map<String, Object>> updateBusiness(@RequestBody Map<String, JsonNode> values) {
        return ResponseEntity.ok(service.updateBusiness(values));
    }
}

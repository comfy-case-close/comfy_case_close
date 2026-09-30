package com.fnbx.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fnbx.platform.service.ConfigService;
import com.fnbx.shared.security.BranchHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {
    private final ConfigService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@RequestHeader(BranchHeader.NAME) UUID branchId) {
        return ResponseEntity.ok(service.get(branchId));
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

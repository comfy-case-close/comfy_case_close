package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.LatePenaltyRuleRequest;
import com.fnbx.hrm.dto.response.LatePenaltyRuleResponse;
import com.fnbx.hrm.service.LatePenaltyRuleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** How many paid hours a late arrival (T1, T2, T3) costs. */
@RestController
@RequestMapping("/late-penalty-rules")
@RequiredArgsConstructor
public class LatePenaltyRuleController {

    private final LatePenaltyRuleService latePenaltyRuleService;

    @GetMapping
    public ResponseEntity<List<LatePenaltyRuleResponse>> list() {
        return ResponseEntity.ok(latePenaltyRuleService.list());
    }

    @PostMapping
    public ResponseEntity<LatePenaltyRuleResponse> create(@Valid @RequestBody LatePenaltyRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(latePenaltyRuleService.create(request));
    }
}

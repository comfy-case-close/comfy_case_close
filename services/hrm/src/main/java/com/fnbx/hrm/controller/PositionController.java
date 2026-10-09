package com.fnbx.hrm.controller;

import com.fnbx.hrm.dto.request.PositionProfileRequest;
import com.fnbx.hrm.dto.response.PositionResponse;
import com.fnbx.hrm.service.PositionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Job positions owned by identity, seen through their payroll profile (department, trainee flag). */
@RestController
@RequestMapping("/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<List<PositionResponse>> list() {
        return ResponseEntity.ok(positionService.list());
    }

    @PutMapping("/{positionId}/payroll-profile")
    public ResponseEntity<PositionResponse> setProfile(@PathVariable UUID positionId,
            @Valid @RequestBody PositionProfileRequest request) {
        return ResponseEntity.ok(positionService.setProfile(positionId, request));
    }
}

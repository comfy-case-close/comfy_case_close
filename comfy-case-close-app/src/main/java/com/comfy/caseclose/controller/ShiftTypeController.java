package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.response.ShiftTypeResponseDTO;
import com.comfy.caseclose.service.ShiftTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shift-types")
@RequiredArgsConstructor
public class ShiftTypeController {

    private final ShiftTypeService shiftTypeService;

    @GetMapping
    public ResponseEntity<List<ShiftTypeResponseDTO>> listShiftTypes() {
        return ResponseEntity.ok(shiftTypeService.listActiveShiftTypes());
    }
}

package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.TipPayoutRequest;
import com.comfy.caseclose.dto.response.TipJarDTO;
import com.comfy.caseclose.dto.response.TipPayoutResultDTO;
import com.comfy.caseclose.service.TipJarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/tip-jar")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
public class TipJarController {

    private final TipJarService tipJarService;

    @GetMapping
    public ResponseEntity<TipJarDTO> getTipJar(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(tipJarService.getTipJar(branchId, fromDate, toDate));
    }

    @PostMapping("/payouts")
    public ResponseEntity<TipPayoutResultDTO> recordPayout(@Valid @RequestBody TipPayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipJarService.recordPayout(request));
    }
}

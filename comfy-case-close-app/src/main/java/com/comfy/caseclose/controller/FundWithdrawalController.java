package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.FundWithdrawalRequest;
import com.comfy.caseclose.dto.response.FundWithdrawalPotDTO;
import com.comfy.caseclose.service.FundWithdrawalService;
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
@RequestMapping("/api/v1/fund-withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
public class FundWithdrawalController {

    private final FundWithdrawalService fundWithdrawalService;

    @GetMapping
    public ResponseEntity<FundWithdrawalPotDTO> getPotData(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String periodType) {
        return ResponseEntity.ok(fundWithdrawalService.getPotData(branchId, fromDate, toDate, periodType));
    }

    @PostMapping
    public ResponseEntity<FundWithdrawalPotDTO> record(@Valid @RequestBody FundWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fundWithdrawalService.record(request));
    }
}

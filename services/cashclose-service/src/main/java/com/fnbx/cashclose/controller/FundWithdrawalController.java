package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.request.FundWithdrawalRequest;
import com.fnbx.cashclose.dto.response.FundWithdrawalPotResponse;
import com.fnbx.cashclose.enums.FundPeriod;
import com.fnbx.cashclose.service.FundWithdrawalService;
import com.fnbx.shared.security.BranchHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/fund-withdrawals")
@RequiredArgsConstructor
public class FundWithdrawalController {
    private final FundWithdrawalService service;

    @GetMapping
    public ResponseEntity<FundWithdrawalPotResponse> getPot(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) FundPeriod periodType) {
        return ResponseEntity.ok(service.getPot(branchId, fromDate, toDate, periodType));
    }

    @PostMapping
    public ResponseEntity<FundWithdrawalPotResponse> record(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @RequestBody FundWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(branchId, request));
    }
}

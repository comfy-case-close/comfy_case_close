package com.fnbx.cashclose.controller;

import com.fnbx.cashclose.dto.request.TipPayoutRequest;
import com.fnbx.cashclose.dto.response.TipJarResponse;
import com.fnbx.cashclose.dto.response.TipPayoutResultResponse;
import com.fnbx.cashclose.service.TipJarService;
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

/** Tip jar is scoped to the selected branch, using the same header as cash closes. */
@RestController
@RequestMapping("/tip-jar")
@RequiredArgsConstructor
public class TipJarController {
    private final TipJarService tipJarService;

    @GetMapping
    public ResponseEntity<TipJarResponse> getTipJar(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(tipJarService.getTipJar(branchId, fromDate, toDate));
    }

    @PostMapping("/payouts")
    public ResponseEntity<TipPayoutResultResponse> recordPayout(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @Valid @RequestBody TipPayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipJarService.recordPayout(branchId, request));
    }
}
